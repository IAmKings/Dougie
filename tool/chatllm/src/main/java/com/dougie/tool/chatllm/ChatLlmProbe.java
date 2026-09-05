package com.dougie.tool.chatllm;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.LogSeverity;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.ThinkingConfig;
import android.content.Context;
import java.io.File;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Sideload LiteRT-LM spike helper. Does not log prompt, completion, or paths. */
public final class ChatLlmProbe {
    public static final String PROMPT = "用一句话介绍你自己";
    private static final int MAX_OUTPUT_TOKENS = 64;
    private static final long GENERATE_TIMEOUT_MS = 30_000L;

    private ChatLlmProbe() {}

    public static File findModel(Context context) {
        File found = firstLitertlm(new File(context.getFilesDir(), "models/chat"));
        if (found != null) {
            return found;
        }
        File external = context.getExternalFilesDir(null);
        if (external == null) {
            return null;
        }
        return firstLitertlm(new File(external, "models/chat"));
    }

    private static File firstLitertlm(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".litertlm"));
        if (files == null || files.length == 0) {
            return null;
        }
        File first = files[0];
        for (File f : files) {
            if (f.getName().compareTo(first.getName()) < 0) {
                first = f;
            }
        }
        return first;
    }

    public static ChatLlmProbeResult run(File model, File cacheDir, boolean gpu) {
        String backendLabel = gpu ? "GPU" : "CPU";
        String modelName = model.getName();
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
        Backend backend = gpu ? new Backend.GPU() : new Backend.CPU(null, null);
        EngineConfig config =
                new EngineConfig(
                        model.getAbsolutePath(),
                        backend,
                        null,
                        null,
                        null,
                        null,
                        cacheDir.getAbsolutePath());
        long loadStart = System.nanoTime();
        Engine engine = new Engine(config);
        try {
            engine.initialize();
            long loadMs = (System.nanoTime() - loadStart) / 1_000_000L;
            Conversation conversation = engine.createConversation(new ConversationConfig());
            try {
                return generate(conversation, modelName, backendLabel, loadMs);
            } finally {
                conversation.close();
            }
        } catch (Throwable t) {
            long loadMs = (System.nanoTime() - loadStart) / 1_000_000L;
            return ChatLlmProbeResult.failed(
                    modelName, backendLabel, loadMs, t.getClass().getSimpleName());
        } finally {
            engine.close();
        }
    }

    private static ChatLlmProbeResult generate(
            Conversation conversation, String modelName, String backendLabel, long loadMs)
            throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        StringBuilder text = new StringBuilder();
        int[] chunks = {0};
        AtomicLong firstTokenNs = new AtomicLong(-1L);
        long genStart = System.nanoTime();
        conversation.sendMessageAsync(
                PROMPT,
                new MessageCallback() {
                    @Override
                    public void onMessage(Message message) {
                        String piece = message.toString();
                        if (piece == null || piece.isEmpty()) {
                            return;
                        }
                        firstTokenNs.compareAndSet(-1L, System.nanoTime());
                        chunks[0] += 1;
                        text.append(piece);
                    }

                    @Override
                    public void onDone() {
                        done.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        error.set(throwable);
                        done.countDown();
                    }
                },
                Collections.emptyMap(),
                null,
                null,
                null,
                MAX_OUTPUT_TOKENS,
                new ThinkingConfig(false));
        boolean finished = done.await(GENERATE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        long elapsedMs = (System.nanoTime() - genStart) / 1_000_000L;
        Long firstTokenMs =
                firstTokenNs.get() < 0 ? null : (firstTokenNs.get() - genStart) / 1_000_000L;
        if (!finished) {
            return ChatLlmProbeResult.timeout(
                    modelName, backendLabel, loadMs, firstTokenMs, elapsedMs, chunks[0], text.toString());
        }
        if (error.get() != null) {
            return ChatLlmProbeResult.failed(
                    modelName,
                    backendLabel,
                    loadMs,
                    error.get().getClass().getSimpleName());
        }
        String output = text.toString();
        if (output.isEmpty()) {
            return new ChatLlmProbeResult(
                    modelName,
                    backendLabel,
                    loadMs,
                    firstTokenMs,
                    elapsedMs,
                    chunks[0],
                    0,
                    null,
                    "失败：空输出",
                    "");
        }
        double charsPerSec = elapsedMs > 0 ? output.length() * 1000.0 / elapsedMs : 0.0;
        return new ChatLlmProbeResult(
                modelName,
                backendLabel,
                loadMs,
                firstTokenMs,
                elapsedMs,
                chunks[0],
                output.length(),
                charsPerSec,
                "成功",
                output);
    }
}
