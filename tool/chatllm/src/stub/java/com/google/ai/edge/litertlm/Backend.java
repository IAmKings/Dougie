package com.google.ai.edge.litertlm;

/** Compile-only stand-in for LiteRT-LM 0.16.1 (class file 65). Not packaged. */
public abstract class Backend {
    public static final class CPU extends Backend {
        public CPU(Integer numOfThreads, Integer threadCount) {}
    }

    public static final class GPU extends Backend {
        public GPU() {}
    }
}
