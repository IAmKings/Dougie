# Implement: 版本 0.1.4 发版

## Order

1. `app/build.gradle.kts`：默认 `0.1.4` / 104。
2. README 两处 v0.1.3 / 103 → v0.1.4 / 104。
3. 提交。`git tag -a v0.1.4 -m "Dougie 0.1.4"`。push `HEAD` 与 tag。

## Validation

远程 tag `v0.1.4`；Actions 出现 Release run。

## Do not

- 改签名、包名、功能。
- `task.py start` 未获规划摘要批准前改文件或打 tag。
