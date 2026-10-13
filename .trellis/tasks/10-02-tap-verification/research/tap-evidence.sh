#!/usr/bin/env bash
# tap 证据采集：抓点击前后的无障碍树文本集差分
# 用法: ./tap-evidence.sh <场景名，如 06-switch-toggle>
set -euo pipefail
NAME="${1:?用法: tap-evidence.sh <场景名>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
DIR="$HERE/evidence"
mkdir -p "$DIR"

dump() { # $1=device path  $2=local path
  if ! adb shell uiautomator dump /sdcard/"$1" >/dev/null 2>&1; then
    echo "uiautomator dump 失败（界面正在动画/滚动？等 2 秒重跑本脚本）" >&2
    exit 1
  fi
  adb pull /sdcard/"$1" "$2" >/dev/null
}

read -rp "① 界面就绪后回车 → 抓 before ..."
dump tap-before.xml "$DIR/$NAME.before.xml"
read -rp "② 现在执行点击，完成后回车 → 抓 after ..."
sleep 1
dump tap-after.xml "$DIR/$NAME.after.xml"

python3 - "$DIR/$NAME.before.xml" "$DIR/$NAME.after.xml" "$DIR/summary.md" "$NAME" <<'PY'
import re, io, sys, datetime

def texts(path):
    xml = io.open(path, encoding='utf-8').read()
    out = set()
    for attr in ('text', 'content-desc'):
        for m in re.finditer(attr + r'="([^"]*)"', xml):
            v = m.group(1).strip()
            if v:
                out.add(v)
    return out

before, after = texts(sys.argv[1]), texts(sys.argv[2])
added = sorted(after - before)
removed = sorted(before - after)
block = "\n".join([
    "## %s  (%s)" % (sys.argv[4], datetime.datetime.now().strftime("%Y-%m-%d %H:%M")),
    "- 新增文本 (%d): %s" % (len(added), added),
    "- 消失文本 (%d): %s" % (len(removed), removed),
    "- 人工判定（填这里）: 点击是否有效？该场景文本集差分能否作为 verified 谓词？",
    "",
])
with io.open(sys.argv[3], 'a', encoding='utf-8') as f:
    f.write(block + "\n")
print(block)
print("已追加到", sys.argv[3])
PY
