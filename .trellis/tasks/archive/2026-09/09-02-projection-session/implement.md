# Implement: 投屏会话

## Checklist

1. `ScreenCaptureService`：会话存活；抓帧不 release VD、不 `stop()`/`stopSelf`；`onStop` 不 `clear()`；ACTION_STOP 才 stop + clear；主线程拆 FGS。
2. `AndroidScreenCapturePort`：二次 capture 复用已启动服务；结束会话 API 供权限中心。
3. 权限中心结束按钮 + MainActivity 接线；副标题。
4. spec `directory-structure.md` 改掉 one-shot 段落；error-handling 仍 PERMISSION_DENIED。
5. 权限文案 JVM 测（若抽出）；`:core:tool` 既有 capture 测仍过。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :feature:permissions:testDebugUnitTest :app:testPlayDebugUnitTest :app:checkChannelLeak
```

真机：授权后连截两次无第二次系统框；结束授权后再截须重授权。

## Risky

- 二次 `getMediaProjection` 会废 token：只调一次。
- 工作线程 `stopForeground`：ColorOS 杀进程。
- 结束会话漏 `clear()`：UI 显示已授权但捕获失败。
