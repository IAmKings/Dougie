package com.google.ai.edge.litertlm;

/** Compile-only stand-in for LiteRT-LM 0.16.1 (class file 65). Not packaged. */
public interface MessageCallback {
    void onMessage(Message message);

    void onDone();

    void onError(Throwable throwable);
}
