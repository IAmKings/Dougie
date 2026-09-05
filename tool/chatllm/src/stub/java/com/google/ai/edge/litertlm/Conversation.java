package com.google.ai.edge.litertlm;

import java.util.Map;

/** Compile-only stand-in for LiteRT-LM 0.16.1 (class file 65). Not packaged. */
public final class Conversation {
    public void sendMessageAsync(
            String text,
            MessageCallback callback,
            Map<?, ?> extraContext,
            RepetitionPenaltyConfig repetitionPenaltyConfig,
            NoRepeatNgramConfig noRepeatNgramConfig,
            SuppressTokensConfig suppressTokensConfig,
            Integer maxOutputToken,
            ThinkingConfig thinkingConfig) {}

    public void close() {}
}
