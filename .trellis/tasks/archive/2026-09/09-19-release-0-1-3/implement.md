# Implement: 版本 0.1.3 发版

## Order

1. `app/build.gradle.kts`：默认 `versionName` `0.1.3`，`versionCode` 103。
2. README 两处 v0.1.2 / 102 → v0.1.3 / 103。
3. 提交 chore。`git tag v0.1.3`。push `HEAD` 与 tag。
4. 不改产品代码。

## Validation

远程 tag `v0.1.3`；Actions 出现 Release run。

## Do not

- 改签名、包名、功能。
- `task.py start` 未获规划摘要批准前改文件或打 tag。
