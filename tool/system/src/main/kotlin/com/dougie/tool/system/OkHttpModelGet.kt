package com.dougie.tool.system

import com.dougie.core.tool.ModelHttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class OkHttpModelGet(
    private val client: OkHttpClient,
) : ModelHttpGet {
    override suspend fun get(
        url: String,
        dest: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val httpUrl = url.toHttpUrlOrNull() ?: throw IOException("download failed")
        if (!httpUrl.isHttps) {
            throw IOException("https only")
        }
        val request = Request.Builder().url(httpUrl).build()
        client.newCall(request).execute().use { response ->
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
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                }
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
