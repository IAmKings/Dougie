# L2 开 App 短路径

## Goal

用户在设置中维护可打开应用名单。高置信 `open_app` 且去套话后整段等于某条别名时，走 L2 确认并以模板结束。`app_intent` 的 `package:` URI 以及非空 `package` 参数（含 LLM）必须落在同一名单；否则拒绝执行。http(s)/geo 的 URI 本身仍只受现有 scheme 规则。

## Background

- `AppIntentTool` 在 `validateArguments` 即走 `AppIntentAllowlist`。现 `geoAndPackageUrisLaunch` 可无名单启动 `package:com.android.settings`，本任务后须注入名单。
- `APP_INTENT_DENIED`（该链接不被允许打开。）不适合「未加入名单」；新增用户可见文案。
- `:tool:system` 已 `<queries>` MAIN+LAUNCHER。`PreferenceStore.save` 会整表写入，名单用独立 key，避免「保存配置」漏拷贝。
- `MainActivity` 用 `AppRoute` 枚举切页（无 Navigation Compose）。

## Requirements

- R1 设置入口「可打开的应用」→ 列表页：图标、别名、包名、添加（启动器应用，可搜索）、改别名、删除。立即落盘。空名单合法。不限制条数。同一包名一条。
- R2 别名默认系统名，可改；去空白后非空。添加只从启动器列表点选，不手输包名。
- R3 短路径：`open_app` 置信度门槛同现网；`normalize` 后去掉「打开/帮我打开/请打开」，剩余整段等于某别名（精确、区分大小写）→ `package:<pkg>`。对不上 → LLM。
- R4 `package:` 与 JSON 非空 `package` 字段：包名必须在名单。不在 → `APP_INTENT_NOT_ALLOWED`，不 execute。http(s)/geo 无 package 字段时不查名单。
- R5 确认拒绝 / 不在前台 / 启动失败：现有 Halt。短路径成功：`已打开{别名}。` + `LOCAL_INTENT`。
- R6 无内置第三方包名。不用 `QUERY_ALL_PACKAGES`。截屏/听写/链接名单不做。

## Acceptance Criteria

- [x] AC1 名单空：「打开微信」即使高置信 `open_app` 仍走 LLM；LLM 若发 `package:com.tencent.mm` → 失败文案为未加入名单，不启动。
- [x] AC2 用户添加某启动器应用并保留别名「微信」：「打开微信」确认后启动 `package:<该包>`，不调 LLM，终答「已打开微信。」
- [x] AC3 「打开微信看看」不短路径；https://example.com 仍可走 LLM/`app_intent`。
- [x] AC4 删除条目后短路径不再命中；`package:` 亦拒绝。
- [x] AC5 Play `checkChannelLeak` 仍过；无 `QUERY_ALL_PACKAGES`。

## Out of scope

- `screen_capture` / `speech_input` 短路径；http(s)/geo 用户名单；改 MiniRBT；模糊/包含匹配。

## Technical notes

- 名单 JSON 存 `PreferenceStore` 独立 key；`:core:tool` 注入 `() -> Set<String>` 或小接口，默认空（CLI/旧测试 https 不变，`package:` 测试改注入）。
- 启动器枚举放 `:app`，注入设置页（与 `OfflineModelProbe` 相同）。
