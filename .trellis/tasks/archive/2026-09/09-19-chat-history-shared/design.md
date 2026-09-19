# Design: Chat↔任务页共享元素转场

## Boundary

`:app` `MainActivity` 管 Chat/History 这一对的转场容器。`:feature:chat` / `:feature:history` 只在用户气泡和任务卡上挂共享 key。不改 TaskManager、`listKey`、忙碌判断。不要 Navigation Compose。不要 Predictive Back 自定义（`consumeBack` 图保持原样）。

## Routing

今天 `when (route)` 互斥，共享元素需要两边短暂同树。把 **Chat 与 History** 收进同一 `SharedTransitionLayout` + `AnimatedContent(targetState = Chat|History)`：

- 卡点击切到 Chat：`sharedBounds`（key = `taskId`）300ms `FastOutSlowIn`。
- 底栏 / `consumeBack`：同一 `AnimatedContent` 用 fade 300ms（`ContentTransform` fadeIn+fadeOut，`sizeTransform = null`），不要给底栏路径塞一个假的 `taskId`。
- 其它 `AppRoute` 仍走外面的 `when`，立刻组合，避免设置页被带动。

`@OptIn(ExperimentalSharedTransitionApi::class)` 只包这一对。缺 animation 依赖时用 BOM 的 `androidx.compose.animation:animation`。

## Keys

HistoryCard：`item.taskId`。
Chat `UserMessage`：从 `listKey` 去掉 `:user` 后缀（已有 `userMessageListKey`）。只给 `UserMessage` 挂，不要给 Thinking/Tool/Agent。

LazyColumn 里目标气泡尚未放置时，Compose 共享会退化成淡入——可接受，不要为对齐去改 `requestFocus` 时序。

## Don't

- 给 Settings/Memory 加 SharedTransition。
- 用 `LazyItemScope.animateItem()` 冒充页面转场。
- 改忙碌时卡点击 no-op。
- 动画时长 0 时仍跑 `translation`/`sharedBounds` 位移。
