package com.dougie.tool.chatllm;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.LogSeverity;
import java.io.File;

/** Java bridge so Kotlin 2.0 can compile against LiteRT-LM 0.16.1 stubs. */
final class ChatLlmEngines {
    private ChatLlmEngines() {}

    static void quietNativeLogs() {
        Engine.Companion.setNativeMinLogSeverity(LogSeverity.ERROR);
    }

    static Engine tryCreate(File model, File cacheDir, boolean gpu) {
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
        Engine engine = new Engine(config);
        try {
            engine.initialize();
            return engine;
        } catch (Throwable ignored) {
            try {
                engine.close();
            } catch (Throwable ignoredClose) {
                // Keep trying CPU after GPU init failure.
            }
            return null;
        }
    }

    static Conversation openConversation(Engine engine) {
        return engine.createConversation(new ConversationConfig());
    }
}
