# MiniRBT intent pack

Self-built Chinese utterances (`train.jsonl`, `heldout.jsonl`) plus:

```bash
python3 core/tool/scripts/intent/generate_corpus.py
HF_HOME=/tmp/huggingface-hub python3 core/tool/scripts/intent/train_export.py --out /tmp/dougie-intent-pack
# MiniRBT 仓库没有 tokenizer_config.json；必须 `BertTokenizer.from_pretrained(snapshot目录)`。
# transformers 5 的 `BertTokenizer(vocab_file=...)` 会丢掉词表，中文全部变成 UNK。
```

Upload the four files to GitHub Release `intent-minirbt-v1` and keep SHA-256 in `OfficialModelCatalog` in sync. Weights stay gitignored.

Seed 42 held-out was 83/88 (~94%). Export needs `torch`, `transformers`, `onnx`. Use `dynamo=False` on PyTorch 2.13+.

Do not `BertTokenizer(vocab_file=...)` on transformers 5 — it drops the vocab. Load `BertTokenizer.from_pretrained(snapshot_dir)` after `hf_hub_download` of `vocab.txt`.
