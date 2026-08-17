# Play / Sideload Channel Flavors

## Goal

用 **构建期 product flavors** 拆出 Play 与 Sideload 两包：不同 `applicationId`、可同机共存；**play 包不含 AccessibilityService、不含 `TapSwipeTool`、不含侧载引导文案**。本切片不实现真实屏幕点击。

## Background

- 产品：`PRD.md` §17.4、§9.5、§10.2。Accessibility 必须静态声明，不能运行时开关。
- 现有 `:app` 无 flavor；`assembleDebug` 需在加 flavor 后仍能编过（play 为 default）。

## Requirements

- **R1** `flavorDimensions += "channel"`；`play`（`isDefault = true`，`applicationId = com.dougie.app`）与 `sideload`（`applicationIdSuffix = ".sideload"`）。`BuildConfig.IS_SIDELOAD`。
- **R2** AccessibilityService + `accessibility_service_config.xml` **仅** `sideload` sourceSet，`exported=false`。play merged manifest / APK 不得出现 `AccessibilityService`、`BIND_ACCESSIBILITY_SERVICE`、`TapSwipeTool` 字符串。
- **R3** `TapSwipeTool` 放在 play **不依赖** 的模块（如 `:tool:accessibility`），仅 `sideloadImplementation`。play 的 Tool Registry 不注册该 Tool。Sideload 可注册；真实点击可暂返回「侧载点击能力尚未启用」类结果，但必须走 L3/每次确认或 onboarding 门闩。
- **R4** play 资源/代码不得含「侧载 / sideload / TapSwipe」引导。Sideload 首次启动显式知情同意（非默认勾选），同意写入本地；未同意不注册或不可执行 TapSwipe。
- **R5** 两包 `assemblePlayDebug` + `assembleSideloadDebug` 均成功。提供可重复的泄漏检查（读 merged manifest 或 `aapt dump xmltree`）。

## Acceptance Criteria

- [ ] `./gradlew :app:assemblePlayDebug :app:assembleSideloadDebug` 通过。
- [ ] play merged manifest 无 AccessibilityService；sideload 有。
- [ ] play APK/dex 或依赖图不含 `:tool:accessibility` / `TapSwipeTool`（至少 manifest + 源码依赖层面）。
- [ ] JVM 既有测试（`:core:runtime:test` 等）不因 flavor 失败。
- [ ] Sideload 同意前不执行 tap；play 构建没有 onboarding 侧载文案资源。

## Out of scope

- 真实 Accessibility 点击/滑动（Phase 5）
- 独立签名证书与应用内自更新
- 离线 ASR 模型按渠道内置

## Constraints

- `:core:*` 仍 JVM-only，且 **不要** 把 `TapSwipeTool` 放进 `:core:tool`（否则会打进 play）。
- 不静默 Fake LLM。
