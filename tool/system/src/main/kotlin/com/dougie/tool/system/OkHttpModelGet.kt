package com.dougie.tool.system

import com.dougie.core.tool.ModelHttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class OkHttpModelGet(
    private val client: OkHttpClient,
) : ModelHttpGet {
    override suspend fun get(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IOException("download failed")
        if (!httpUrl.isHttps) {
            throw IOException("https only")
        }
        val call = client.newCall(Request.Builder().url(httpUrl).build())
        withContext(Dispatchers.IO) {
            val handle = coroutineContext.job.invokeOnCompletion { cause ->
                if (cause != null) call.cancel()
            }
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("download failed")
                    }
                    val body = response.body ?: throw IOException("empty body")
                    val total = body.contentLength()
                    dest.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buf = ByteArray(64 * 1024)
                            var downloaded = 0L
                            while (true) {
                                ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                output.write(buf, 0, n)
                                downloaded += n
                                onProgress(downloaded, total)
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                throw e
            } finally {
                handle.dispose()
            }
        }
    }

    companion object {
        fun client(base: OkHttpClient): OkHttpClient = base.newBuilder()
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                if (!chain.request().url.isHttps) {
                    throw IOException("https only")
                }
                chain.proceed(chain.request())
            }
            .build()
    }
}
