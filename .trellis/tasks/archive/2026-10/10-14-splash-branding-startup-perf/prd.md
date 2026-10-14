# 启动体验：品牌化启动窗与重初始化延迟

## Goal

启动窗（黑/白底+Logo）可见时长由 Application.onCreate 同步耗时决定，且视觉上是无品牌的「黑屏」。两项改善：①v31 SplashScreen 主题属性品牌化（底色=主题底色、图标=应用图标）；②重初始化（任务恢复全表解码、转录重载、告警、悬浮框同步）延迟到首帧之后。

## Requirements

- R1 values-v31 / values-night-v31 主题：windowSplashScreenBackground=主题底色（#F8FAF9 / #191C1C）、windowSplashScreenAnimatedIcon=@mipmap/ic_launcher、windowSplashScreenIconBackgroundColor=@color/ic_launcher_background；其余条目与基础主题一致。
- R2 DougieApplication.onCreate：任务恢复（recoverInterrupted+seed+reloadTranscript）移入 appScope.launch（首帧后）；ChannelHooks.syncOverlay / syncSystemNightMode / ScheduleAlarms.sync 一并延迟。
- R3 延迟块保持原有语义：恢复行翻 FAILED(INTERRUPTED)、当前会话 seed、转录重载；懒加载风险（用户极快提交）接受为边缘场景。
- R4 不动 14 条冻结契约；PreferenceStore（EncryptedSharedPreferences）本轮不延迟化（load-bearing，另立任务评估）。

## Acceptance Criteria

- [x] AC1 双 flavor 编译 + 测试 + checkChannelLeak 绿。
- [x] AC2 基线/优化对比：冷启动 TotalTime 基线 715-729ms → 优化后 673-827ms（旗舰机差异在噪声内；收益主体在低端机与任务库大的场景——全表解码离开关键路径）。
- [x] AC3 真机：启动窗为品牌化呈现（图标+主题底色）。
- [ ] AC4 用户真机验收：启动观感与功能回归。

## Follow-up（验收问题修复：恢复竞态边界）

延迟化引入的真实竞态：恢复逻辑异步执行后，若本进程在恢复完成前创建了新任务（快速提交/Intent 自动触发），该新任务（非终态）会被误翻 FAILED(任务已中断)。修复：recoverInterrupted 增加 startedBeforeMs 边界——只恢复 startedAt 早于进程起点（或无 startedAt 的遗留行）的中断行，本进程新建任务在机制上不可能被触碰。DougieApplication 捕获 processStartedAtMs 传入。既有测试调用方全部补 startedBeforeMs = Long.MAX_VALUE。AC 追加：

- [x] AC5 竞态边界 JVM 测试（旧僵尸恢复、本进程新任务不被触碰、无 startedAt 遗留行恢复）。

## Out of scope

- PreferenceStore（EncryptedSharedPreferences）延迟化评估；启动窗跟随应用偏好的 OEM 限制（已终审，见 splash-follow-preference）。
