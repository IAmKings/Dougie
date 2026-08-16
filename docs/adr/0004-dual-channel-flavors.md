# 双渠道构建期打包(play 无 Accessibility / sideload 完整)

同一产品产出两个渠道包:**play 包**(无 Accessibility 功能,合规上架)与 **sideload 包**(含 `TapSwipeTool`,侧载发布,重度用户手动赋权并完全知情)。差异在**构建期**通过 Gradle product flavors 固化(applicationId 隔离、sourceSet 分离、BuildConfig 开关过滤 Tool Registry),不做运行时切换。理由:AccessibilityService 必须在 `AndroidManifest.xml` 静态声明、运行时无法动态注册/解锁;且 Google Play 政策禁止自动化点击第三方 App。

**Status**: accepted

**Considered Options**: 单渠道(要么失去合规、要么失去能力);运行时动态解锁 Accessibility(平台不支持)。
