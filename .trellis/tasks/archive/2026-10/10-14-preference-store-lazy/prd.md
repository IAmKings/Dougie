# PreferenceStore 延迟化（启动耗时大头）

## Goal

`PreferenceStore` 构造函数同步初始化 EncryptedSharedPreferences（AndroidKeyStore 密钥生成 + 加密读写），是冷启动 `TotalTime ~700ms` 中最大的一块同步开销。目标：将其延迟到**首次实际访问**时初始化，启动路径只保留轻量代理。

## Confirmed facts

- `DougieApplication.onCreate` 中 `preferenceStore = PreferenceStore(this)` 同步执行。
- `PreferenceStore` init 块：MasterKey.Builder + EncryptedSharedPreferences.create + 4 个 MutableStateFlow 初始化（含 `read()` 全量读取）。AndroidKeyStore 操作在主线程，实测 100-300ms。
- `lateinit var preferenceStore` 无 `private set`——可替换为 `by lazy`。
- 访问方分三类：
  ① Application.onCreate 内部（ports/回调 lambda——延迟到调用时）；
  ② Activity/Fragment Composable（`collectAsStateWithLifecycle`——延迟到组合时）；
  ③ MainActivity `attachBaseContext` 的 `themePreference()`——**在 super.onCreate 之前**，必须处理（未初始化时返回 SYSTEM 即可，已有 `::preferenceStore.isInitialized` 守卫）。
- `themePreference()` 已有 `if (!::preferenceStore.isInitialized) return ThemePreference.SYSTEM` 守卫，说明代码已预留了未初始化路径。

## Requirements

- R1 `DougieApplication.preferenceStore` 从 `lateinit var` 改为 `val preferenceStore: PreferenceStore by lazy { PreferenceStore(this) }`——首次访问时初始化，线程安全（lazy 默认 synchronized）。
- R2 `themePreference()` 中 `::preferenceStore.isInitialized` 守卫相应调整（lazy 属性没有 isInitialized，改用 `SystemClock.elapsedRealtime()` 或直接删——lazy 首次访问在 attachBaseContext 之后，themePreference() 在 attachBaseContext 之前调用，需要保证不触发 lazy 初始化）。
- R3 其他调用方（tools lambda、providers、ViewModels、SettingsRoute）零改动——lazy 透明。
- R4 冷启动 `TotalTime` 下降 ≥100ms（am start -W 前后对比）。

## Acceptance Criteria

- [ ] AC1 双 flavor 编译 + 全量测试绿。
- [ ] AC2 `am start -W` TotalTime 前后对比 ≥100ms 下降。
- [ ] AC3 真机：设置页读写正常、主题切换正常、LLM 连接正常。
