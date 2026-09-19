# Implement: Confirm 卡离场动效

## Order

1. `nextConfirmExit` + `ChatUiStateTest`（首帧不播；有→无 play；仍有同一 key 不播）。
2. `ChatScreen` 在 Confirm 消失后留下最后一张卡，播 250ms 下滑+淡出。
3. 时长 0 只淡出。不推迟 ViewModel 确认/拒绝/取消。
4. spec：component / hook / state-management（离场与进场成对；首帧不播）。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :feature:chat:testDebugUnitTest
```

JDK 17。真机：打开微信确认/拒绝/终止都下滑收起；确认后工具卡可同时出现；回对话不播离场。

## Do not

- 倒计时 UI、点压暗=拒绝、等动画结束再调用 TaskManager。
- `task.py start` 未获规划摘要批准前改产品代码。
