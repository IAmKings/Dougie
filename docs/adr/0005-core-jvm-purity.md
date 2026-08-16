# :core:* 保持 JVM 纯净(零 android.* 依赖)

`:core:runtime / model / policy / memory / llm / tool / security` 保持纯 JVM,不依赖任何 `android.*` API。理由:让 Agent Runtime 可被 `:cli`(开发控制台,mosaic)直接复用,并为未来桌面端(决策 #18)预留复用路径;`:cli` 能否复用 `:core:*` 本身就是模块解耦的验证手段。

**边界**:`:core:security` 只承载平台无关的安全逻辑(数据分类、EgressPolicy 判定、日志脱敏规则、Risk Level 分级);平台相关的密钥存储(Android Keystore)作为 `SecureStorage` **接口**在 `:core:security` 定义,实现下沉到 `:data:` 层(Android 实现),以守住 JVM 纯净红线。

**Status**: accepted

**Considered Options**: 直接在 `:feature:*` 层实现(无法跨端复用,桌面端成本倍增);`:core:security` 直接调用 Android Keystore(违反 JVM 纯净红线)。
