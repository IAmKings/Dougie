package com.dougie.tool.chatllm;

public final class ChatLlmProbeResult {
    public final String modelName;
    public final String backend;
    public final Long loadMs;
    public final Long firstTokenMs;
    public final Long elapsedMs;
    public final int chunkCount;
    public final int chars;
    public final Double charsPerSec;
    public final String status;
    public final String output;

    public ChatLlmProbeResult(
            String modelName,
            String backend,
            Long loadMs,
            Long firstTokenMs,
            Long elapsedMs,
            int chunkCount,
            int chars,
            Double charsPerSec,
            String status,
            String output) {
        this.modelName = modelName;
        this.backend = backend;
        this.loadMs = loadMs;
        this.firstTokenMs = firstTokenMs;
        this.elapsedMs = elapsedMs;
        this.chunkCount = chunkCount;
        this.chars = chars;
        this.charsPerSec = charsPerSec;
        this.status = status;
        this.output = output;
    }

    public static ChatLlmProbeResult failed(
            String modelName, String backend, Long loadMs, String errorKind) {
        String status = errorKind == null || errorKind.isEmpty() ? "失败" : "失败：" + errorKind;
        return new ChatLlmProbeResult(
                modelName, backend, loadMs, null, null, 0, 0, null, status, "");
    }

    public static ChatLlmProbeResult timeout(
            String modelName,
            String backend,
            Long loadMs,
            Long firstTokenMs,
            Long elapsedMs,
            int chunkCount,
            String output) {
        return new ChatLlmProbeResult(
                modelName,
                backend,
                loadMs,
                firstTokenMs,
                elapsedMs,
                chunkCount,
                output.length(),
                null,
                "超时",
                output);
    }
}
