# Implement — :cli mosaic Console

1. `libs.versions.toml`：mosaic **0.14.0**（not 0.18.0 — Kotlin 2.2 metadata vs repo 2.0.21）、kotlinx-cli 版本。
2. `settings.gradle.kts` `include(":cli")`；`cli/build.gradle.kts` application + 依赖 `:core:runtime`。
3. `Cli.kt`：参数 `--log-only`；装配 LoopEngine/TaskManager；提交电量句；收集 StateFlow 直到 COMPLETED/FAILED。
4. mosaic 界面：状态 + loopCount + 最多最近 3 条 toolTrace + 终答/错误；catch 后降级 println。
5. 单测：JVM 测装配后 `TaskManager.submit` 在测试 dispatcher 上 COMPLETED 且 3 次 battery（可 `stepDelayMs=0`），不强制测 mosaic 像素。
6. 文档：根 `README.md` 加一节 `:cli` 命令。
7. 验证：
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   ./gradlew :cli:test :cli:run --args='--log-only'
   ./gradlew :app:assemblePlayDebug   # 确认不依赖 :cli
   ```

## 风险

- mosaic 版本与 Kotlin 2.0 不兼容：换版本或本切片只保留 `--log-only` 仍算 AC（须在实现时写进 README）。
- 勿把 `:tool:system` 链进 CLI。
