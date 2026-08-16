#!/usr/bin/env python3
"""PRD.md 校验器 — 执行验收基线的静态一致性检查。

用法(项目测试命令):
  cd <repo> && python3 -m unittest discover -s .trellis/scripts -p 'check_prd.py' -v

也可直接运行 CLI:
  python3 .trellis/scripts/check_prd.py [path/to/PRD.md]
退出码: 0 = 全部通过, 1 = 存在失败项。
注意: 表格总数/分隔行/版本等为基线锁定断言,PRD 结构变更时需同步更新本脚本。
"""
import re
import sys
import unittest

DEFAULT_PATH = "PRD.md"


def load(path: str) -> str:
    with open(path, encoding="utf-8") as f:
        return f.read()


def collect(path: str) -> dict:
    """返回可断言的事实字典,供 unittest 与 CLI 共用。"""
    text = load(path)
    lines = text.split("\n")
    cases = re.findall(r"^### Case \d+$", text, flags=re.M)
    sep_rows = sum(1 for l in lines if l.startswith("|---"))
    tables, i = [], 0
    while i < len(lines):
        if re.match(r"^\s*\|", lines[i]) and i + 1 < len(lines) and re.match(
            r"^\s*\|[\s\-:|]+\|\s*$", lines[i + 1]
        ):
            header_cols = lines[i].count("|") - 1
            sep_cols = lines[i + 1].count("|") - 1
            j, issues = i + 2, []
            while j < len(lines) and re.match(r"^\s*\|", lines[j]):
                if lines[j].count("|") - 1 != header_cols:
                    issues.append(j + 1)
                j += 1
            tables.append((i + 1, header_cols == sep_cols and not issues))
            i = j
        else:
            i += 1
    ids = re.findall(r"^\| (1[4-9]|2[0-9]) \|", text, flags=re.M)
    unique_ids = sorted({int(x) for x in ids})
    return {
        "text": text,
        "case_count": len(cases),
        "code_fence_pairs": text.count("```") % 2 == 0,
        "sep_rows": sep_rows,
        "table_count": len(tables),
        "tables_consistent": all(t[1] for t in tables),
        "decision_ids": unique_ids,
        "has_rules": {r: r in text for r in ("规则 A", "规则 B", "规则 C", "规则 D", "规则 E")},
        "has_phase5": "Phase 5（Beta，非 MVP 阻塞）" in text,
        "has_mic_privacy": "play 包语音能力隐私声明" in text,
        "refs": {r: r in text for r in ("§6.7", "§6.8", "§6.9", "§17.4", "§10.2", "§9.5")},
        "version_exact": re.search(r"^\| 文档版本 \| V2\.1\.10（", text, flags=re.M) is not None,
    }


class TestPRD(unittest.TestCase):
    def setUp(self):
        self.f = collect(DEFAULT_PATH)

    def test_version(self):
        self.assertTrue(self.f["version_exact"], "文档版本 V2.1.10 精确匹配失败")

    def test_e2e_case_count(self):
        self.assertEqual(self.f["case_count"], 14, "E2E Case 数量应为 14")

    def test_code_fences_paired(self):
        self.assertTrue(self.f["code_fence_pairs"], "代码块应配对(偶数)")

    def test_table_sep_rows(self):
        self.assertEqual(self.f["sep_rows"], 32, "表格分隔行应为 32")

    def test_table_count_and_consistency(self):
        self.assertEqual(self.f["table_count"], 32, "表格总数应为 32")
        self.assertTrue(self.f["tables_consistent"], "全部表格列应一致")

    def test_decision_ids_continuous(self):
        self.assertEqual(self.f["decision_ids"], list(range(14, 23)), "决策编号应连续 #14–#22")

    def test_decision_refs_in_range(self):
        # "决策 #N"引用不得悬空:V1→V2 历史决策 #1–#13(§1.4)与追加决策 #14–#22 均合法,
        # 超出 1–22 的编号视为悬空引用
        refs = re.findall(r"决策 #(\d+)", self.f["text"])
        out_of_range = sorted({int(n) for n in refs if not (1 <= int(n) <= 22)})
        self.assertEqual(out_of_range, [], f"存在悬空决策引用 {out_of_range}(应在 #1–#22)")

    def test_rules_landed(self):
        for r, ok in self.f["has_rules"].items():
            self.assertTrue(ok, f"定标规则 {r} 应落地")

    def test_phase5_and_privacy(self):
        self.assertTrue(self.f["has_phase5"], "Phase 5 Beta 完成标准应落地")
        self.assertTrue(self.f["has_mic_privacy"], "play 包麦克风隐私声明应落地")

    def test_cross_refs(self):
        for r, ok in self.f["refs"].items():
            self.assertTrue(ok, f"交叉引用锚点 {r} 应存在")


class TestADR(unittest.TestCase):
    def test_adr_count_and_numbering(self):
        import glob
        import os
        files = sorted(glob.glob(os.path.join("docs", "adr", "*.md")))
        self.assertEqual(len(files), 6, f"ADR 应为 6 个(实际 {len(files)})")
        nums = [int(os.path.basename(f)[:4]) for f in files]
        self.assertEqual(nums, list(range(1, 7)), f"ADR 编号应 0001–0006 连续(实际 {nums})")

    def test_adr_status(self):
        import glob
        import os
        for f in sorted(glob.glob(os.path.join("docs", "adr", "*.md"))):
            with open(f, encoding="utf-8") as fh:
                self.assertIn("**Status**: accepted", fh.read(), f"{f} 应含 accepted 状态")


class TestContext(unittest.TestCase):
    def test_context_exists_and_terms(self):
        import os
        self.assertTrue(os.path.exists("CONTEXT.md"), "CONTEXT.md 应存在")
        with open("CONTEXT.md", encoding="utf-8") as fh:
            content = fh.read()
        terms = re.findall(r"^\*\*[^*]+\*\*:$", content, flags=re.M)
        self.assertGreaterEqual(len(terms), 33, f"术语应 ≥ 33 条(实际 {len(terms)})")
        for t in ("Loop Engine", "State Machine", "Context Builder",
                  "ToolCallSanitizer", "Process Death Recovery",
                  "IntentClassifierTool",
                  "play 包", "sideload 包", "EgressPolicy", "UNTRUSTED_DATA"):
            self.assertIn(t, content, f"术语 {t} 应存在")

    def test_context_avoid_completeness(self):
        with open("CONTEXT.md", encoding="utf-8") as fh:
            lines = fh.read().split("\n")
        term_rows = [i for i, l in enumerate(lines) if re.match(r"^\*\*[^*]+\*\*:$", l)]
        for i in term_rows:
            has_avoid = (i + 2 < len(lines) and lines[i + 2].startswith("_Avoid_:"))
            self.assertTrue(has_avoid, f"术语「{lines[i]}」后应紧跟 _Avoid_ 指引")


class TestEncoding(unittest.TestCase):
    def test_all_docs_utf8(self):
        import glob
        import os
        paths = (["PRD.md", "CONTEXT.md"]
                 + sorted(glob.glob(os.path.join("docs", "adr", "*.md")))
                 + sorted(glob.glob(os.path.join("source", "*.md"))))
        self.assertGreaterEqual(len(paths), 11, f"应覆盖 ≥ 11 个文档(实际 {len(paths)})")
        for p in paths:
            with open(p, encoding="utf-8") as fh:
                content = fh.read()
            self.assertNotIn("\ufffd", content, f"{p} 含 UTF-8 替换符(编码损坏)")


def main(argv: list[str]) -> int:
    facts = collect(argv[1] if len(argv) > 1 else DEFAULT_PATH)
    checks = [
        (facts["version_exact"], "文档版本 V2.1.10 生效(精确匹配)"),
        (facts["case_count"] == 14, f"E2E Case 数量 = 14 (实际 {facts['case_count']})"),
        (facts["code_fence_pairs"], "代码块配对为偶数"),
        (facts["sep_rows"] == 32, f"表格分隔行 = 32 (实际 {facts['sep_rows']})"),
        (facts["table_count"] == 32, f"表格总数 = 32 (实际 {facts['table_count']})"),
        (facts["tables_consistent"], "全部表格列一致(0 异常)"),
        (facts["decision_ids"] == list(range(14, 23)), f"决策编号连续 #14–#22 (实际 {facts['decision_ids']})"),
    ]
    checks += [(ok, f"定标规则 {r} 落地") for r, ok in facts["has_rules"].items()]
    checks += [
        (facts["has_phase5"], "Phase 5 Beta 完成标准落地"),
        (facts["has_mic_privacy"], "play 包麦克风隐私声明落地"),
    ]
    checks += [(ok, f"交叉引用锚点 {r} 存在") for r, ok in facts["refs"].items()]
    failures = 0
    for cond, label in checks:
        print(("PASS" if cond else "FAIL") + " - " + label)
        failures += 0 if cond else 1
    print(f"\n结果: {len(checks) - failures} PASS, {failures} FAIL")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
