package com.dougie.tool.chatllm;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sideload-only LiteRT-LM spike. Not on the product chat path.
 * Launch: adb shell am start -n com.dougie.app.sideload/com.dougie.tool.chatllm.ChatLlmSpikeActivity
 * (exported=true; sideload only)
 */
public final class ChatLlmSpikeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView metrics;
    private TextView output;
    private Button cpu;
    private Button gpu;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        metrics = new TextView(this);
        output = new TextView(this);
        LinearLayout buttons = new LinearLayout(this);
        cpu = new Button(this);
        cpu.setText("CPU");
        gpu = new Button(this);
        gpu.setText("GPU");
        buttons.addView(cpu);
        buttons.addView(gpu);
        root.addView(metrics);
        root.addView(buttons);
        root.addView(output);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        render(idleUi());
        cpu.setOnClickListener(v -> runProbe(false));
        gpu.setOnClickListener(v -> runProbe(true));
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    private void runProbe(boolean useGpu) {
        if (busy) {
            return;
        }
        File model = ChatLlmProbe.findModel(this);
        if (model == null) {
            render(
                    "LiteRT-LM 探针（侧载）\n"
                            + "模型 （无）\n"
                            + "状态 失败：未找到 .litertlm\n"
                            + "把 .litertlm 放到内部 filesDir/models/chat/ 或\n"
                            + "Android/data/…sideload/files/models/chat/");
            output.setText("");
            return;
        }
        busy = true;
        setButtonsEnabled(false);
        render(
                "LiteRT-LM 探针（侧载）\n"
                        + "模型 "
                        + model.getName()
                        + "\n后端 "
                        + (useGpu ? "GPU" : "CPU")
                        + "\n状态 加载中…");
        output.setText("");
        worker.execute(
                () -> {
                    ChatLlmProbeResult result = ChatLlmProbe.run(model, getCacheDir(), useGpu);
                    runOnUiThread(
                            () -> {
                                busy = false;
                                setButtonsEnabled(true);
                                render(format(result));
                                output.setText(result.output == null ? "" : result.output);
                            });
                });
    }

    private void setButtonsEnabled(boolean enabled) {
        cpu.setEnabled(enabled);
        gpu.setEnabled(enabled);
    }

    private static String idleUi() {
        return "LiteRT-LM 探针（侧载）\n模型 （无）\n后端 CPU\n加载 —\n首 token —\n生成 —\n状态 就绪";
    }

    private static String format(ChatLlmProbeResult r) {
        return "LiteRT-LM 探针（侧载）\n"
                + "模型 "
                + r.modelName
                + "\n后端 "
                + r.backend
                + "\n加载 "
                + ms(r.loadMs)
                + "\n首 token "
                + ms(r.firstTokenMs)
                + "\n生成 "
                + ms(r.elapsedMs)
                + " · 片段 "
                + r.chunkCount
                + " · 字符 "
                + r.chars
                + "\n字符/秒 "
                + (r.charsPerSec == null ? "—" : String.format(Locale.US, "%.1f", r.charsPerSec))
                + "（非 tok/s）\n状态 "
                + r.status;
    }

    private static String ms(Long value) {
        return value == null ? "—" : value + " ms";
    }

    private void render(String text) {
        metrics.setText(text);
    }
}
