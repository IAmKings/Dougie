# Phase 3b Location + Screen Sense

## Goal

补齐 Phase 3 剩余感知能力：**粗定位**、**屏幕截取（仅本地）**、**模板匹配（read-only）**。截图不得进入 Prompt；匹配只返回结构化 JSON。不含 Accessibility 点击。

## Background

- 依赖：已归档 `08-17-phase-3a-tools-policy`（Policy、确认卡、权限中心）。
- 产品：`PRD.md` §6.7、§9.1 屏幕 Sensitive、Location Tool。
- 视觉：`design/权限与系统/地理位置访问_-_隐私围栏引导_中文版__*.html`。

## Requirements

- **R1** `location` Tool（L1，`ACCESS_COARSE_LOCATION`）：返回 JSON（纬度、经度可降精度、accuracy、provider）。无权限走现有 Policy skip。不 log 坐标。
- **R2** `screen_capture`（L1）：仅前台。成功 ToolResult **不得含像素/base64**，只含 `capture_id` / width / height。像素留在进程内 `ScreenFrameStore`。未授予 MediaProjection → PermissionDenied 用户文案。Android 14+ 若使用 VirtualDisplay，需符合 `foregroundServiceType=mediaProjection`。
- **R3** `screen_match`（L0）：参数 `template_id`。无最近截图或置信度低于阈值 → 失败 JSON / fatal，**不得猜测**。输出 `{template_id, found, x, y, confidence}`。匹配结果视为 UNTRUSTED_DATA，不要当指令执行。
- **R4** 不引入 OpenCV AAR（避免 10–20MB）。用 JVM 可测的灰度归一化互相关（或等价）实现匹配；Android 把 Bitmap 转成灰度字节交给同一算法。
- **R5** Permission Center 增加：定位运行时权限；屏幕截取引导（启动系统 MediaProjection 同意）。Chat 锁/权限页能看到新条目。
- **R6** 现有 L0 Fake 三连与 L2 确认测试保持绿色。

## Acceptance Criteria

- [ ] JVM：LocationPort fake 返回固定坐标 JSON；缺权限 Policy 不 execute。
- [ ] JVM：ScreenFrameStore 存灰度帧；`screen_match` 在合成图上能 found=true；无帧时失败不猜测。
- [ ] JVM：`screen_capture` ToolResult 字符串不含 `data:image` / 长 base64。
- [ ] OpenAI tools 列表含 `location` / `screen_capture` / `screen_match`。
- [ ] `./gradlew :core:runtime:test :core:tool:test :app:assembleDebug` 通过。

## Out of scope

- `TapSwipeTool` / Accessibility（Beta）
- OpenCV 依赖与模板库产品化
- 截图加密落盘（内存帧即可）
- App Intent（若本轮来不及，单列后续；优先本切片三工具）
- Phase 4 杀进程恢复

## Constraints

- `:core:*` JVM-only。
- 截图不进 LLM messages、不进 Logcat、不进 Memory Gate。
- 不静默 Fake LLM。
