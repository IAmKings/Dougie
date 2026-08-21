#!/usr/bin/env python3
"""Write a tiny Gemm ONNX classifier for the hashed n-gram bag featurizer."""

from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper

FNV_OFFSET = 0x811C9DC5
FNV_PRIME = 0x01000193


def fnv1a32(data: bytes) -> int:
    h = FNV_OFFSET
    for b in data:
        h ^= b
        h = (h * FNV_PRIME) & 0xFFFFFFFF
    return h


def featurize(text: str, dim: int, nmin: int, nmax: int) -> list[float]:
    x = [0.0] * dim
    for n in range(nmin, nmax + 1):
        for i in range(0, len(text) - n + 1):
            gram = text[i : i + n].encode("utf-8")
            x[fnv1a32(gram) % dim] += 1.0
    return x


def main() -> int:
    here = Path(__file__).resolve().parent
    spec = json.loads((here / "tokenizer.json").read_text(encoding="utf-8"))
    labels = [
        line.strip()
        for line in (here / "labels.txt").read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]
    dim = int(spec["dim"])
    nmin = int(spec["ngram_min"])
    nmax = int(spec["ngram_max"])
    x = np.array(featurize("现在几点", dim, nmin, nmax), dtype=np.float32)
    weights = np.zeros((len(labels), dim), dtype=np.float32)
    weights[0] = x
    bias = np.zeros((len(labels),), dtype=np.float32)
    graph = helper.make_graph(
        nodes=[
            helper.make_node(
                "Gemm",
                inputs=["features", "W", "B"],
                outputs=["logits"],
                name="intent_gemm",
                transB=1,
            ),
        ],
        name="intent",
        inputs=[helper.make_tensor_value_info("features", TensorProto.FLOAT, [1, dim])],
        outputs=[helper.make_tensor_value_info("logits", TensorProto.FLOAT, [1, len(labels)])],
        initializer=[
            numpy_helper.from_array(weights, name="W"),
            numpy_helper.from_array(bias, name="B"),
        ],
    )
    model = helper.make_model(
        graph,
        producer_name="dougie",
        ir_version=8,
        opset_imports=[helper.make_opsetid("", 13)],
    )
    onnx.checker.check_model(model)
    out = here / "model.onnx"
    onnx.save(model, str(out))
    print(f"wrote {out} bytes={out.stat().st_size} query_time_logit={float(x @ x):.3f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
