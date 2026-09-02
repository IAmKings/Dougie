# Implement: 侧载悬浮球截屏可发现性

## Checklist

1. 改 sideload `overlay_body`；单测/字符串断言：不再含「点按打开对话」，含截屏与授权。Play `PlayShortcutCopyTest` 仍禁「上层显示」。
2. `DougieOverlayService`：单击展开两项；拖动不展开；截屏/开聊分支；缺权路径。
3. `ChannelHooks.screenShortcutHint` + Chat 展示；Play 恒 null。`ChatUiState` 映射保持纯 `AgentTask`。
4. 权限中心侧载「上层显示」槽；Play 不出现。
5. 更新 `.trellis/spec/frontend/quality-guidelines.md`（overlay 单击展开，不再单击即截）与 `logging-guidelines.md`（菜单固定中文，仍无 prompt）。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :app:testPlayDebugUnitTest :app:checkChannelLeak :feature:chat:test :feature:permissions:testDebugUnitTest
```

（permissions 若无测试模块则跳过该 task，改在 `:app` 测 extra 列表为空/非空。）

真机侧载：开球 → 他 App 展开截屏 → 芯片；无投屏 → 失败引导；Chat 短路径「截个屏」见引导句且播报不含引导。

## Risky files

- `DougieOverlayService`：焦点/触摸穿透导致误点下层 App。
- 勿把引导句写入 `finalAnswer` 或 Play strings。
