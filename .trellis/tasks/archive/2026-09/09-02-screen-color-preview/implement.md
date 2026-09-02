# Implement: 截屏预览默认彩色

## Checklist

1. 截屏路径产出 JPEG + gray；`ChatAttachmentSession.addScreen` 保存 jpeg。
2. `MainActivity` 预览 SCREEN 用 `jpegPreview`。
3. Fake/测试：彩色帧预览非灰度；`OpenAICompatibleProviderTest` SCREEN 仍无 image；`:core:tool` match 回归。
4. spec `directory-structure`：SCREEN 预览 JPEG 本机，gray 仅 match；不上云。

## Validation

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew :core:tool:test :core:llm:test :app:testPlayDebugUnitTest :app:testSideloadDebugUnitTest
```

真机：截屏预览彩色；匹配仍可用。

## Risky

- 勿把 SCREEN JPEG 交给 `attachmentJpeg` 给云。
- 内存：最多 4 张 JPEG+gray；保持 MAX=4。
