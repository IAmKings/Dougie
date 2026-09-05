package com.google.ai.edge.litertlm;

/** Compile-only stand-in for LiteRT-LM 0.16.1 (class file 65). Not packaged. */
public final class Engine {
    public static final Companion Companion = new Companion();

    public Engine(EngineConfig config) {}

    public void initialize() {}

    public Conversation createConversation(ConversationConfig config) {
        return null;
    }

    public void close() {}

    public static final class Companion {
        public void setNativeMinLogSeverity(LogSeverity severity) {}
    }
}
