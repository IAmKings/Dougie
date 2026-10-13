# 清单内应用强制 package 打开

## Goal

复测：「打开Anchor」时云端模型凭世界知识把 Anchor 当成 https://anchor.fm 链接（打开失败），无视环境块里的 Anchor=fm.anchor.android。环境块只给了清单没给使用规则。修复：环境块与 app_intent 工具描述双重写明「清单内应用必须用 package: URI」。

## Confirmed facts

- 设备复测：打开失败，但验证层未撒谎（https 无包名 → verified=null 诚实未知，无假成功）。
- AppIntentAllowlist 对 https 仅要求 host 非空（自由放行），package: 才查名单——模型选 https 绕开了名单闸，属提示词歧义而非校验缺口（https 校验收紧会让「打开网页」场景误伤，不动）。
- app_intent 描述与本地教学清单都会进双路径 prompt——描述措辞是零成本杠杆。

## Requirements

- R1 PlannerEnvironment.build 追加定向规则：「用户提到的应用在此清单中时，必须用其包名构造 package: URI 打开（如 package:fm.anchor.android），不要改用链接；清单外的网址才用 https。」MAX_LENGTH 600→700。
- R2 AppIntentTool.DESCRIPTOR.description 追加「For apps named in the user's openable-apps list, use their package: URI, never a web link.」
- R3 不动 AppIntentAllowlist 校验逻辑、不动 14 条冻结契约（jsonl 不涉描述文本）、无新增日志。

## Acceptance Criteria

- [x] AC1 JVM 测试：环境块含 package 定向规则句；700 上限生效。
- [x] AC2 `:core:llm:test :core:tool:test :app:testSideloadDebugUnitTest :app:checkChannelLeak` 绿；冻结契约不动。
- [x] AC3 真机复测：「打开Anchor」→ app_intent 收到 package:fm.anchor.android → 打开成功且（若前台验证通过）verified=true。

## Out of scope

- https URI 的输入锚定重映射（「打开X这个链接」类网页意图会误伤）；App Links 域名校验。
