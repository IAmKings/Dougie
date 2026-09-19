# Implement: Chat↔任务页共享元素转场

## Order

1. `:app`：Chat/History 放进 `SharedTransitionLayout` + `AnimatedContent`（300ms FastOutSlowIn）。其它路由仍 `when` 立刻切。
2. `HistoryCard`：`sharedBounds` key = `taskId`。
3. Chat `UserMessage`：同一 key（`listKey` 去掉 `:user`）。
4. 底栏 / `consumeBack` 走淡入；卡点击走共享。时长 0 只淡入。
5. `AppBackNav` 图不变。补 animation 依赖如需要。
6. spec：component / directory-structure（Chat+History 转场容器在 `:app`）。

## Validation

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export GRADLE_USER_HOME="$HOME/.gradle"
./gradlew :app:testPlayDebugUnitTest :feature:chat:testDebugUnitTest :feature:history:testDebugUnitTest
```

JDK 17。真机：点卡卡飞向用户气泡并定位；底栏来回淡入；进设置仍硬切；关动画只淡入；忙碌不打开。

## Do not

- Predictive Back 自定义、Nav Compose、记忆/设置共享、改 `listKey` / 忙碌。
- `task.py start` 未获规划摘要批准前改产品代码。
