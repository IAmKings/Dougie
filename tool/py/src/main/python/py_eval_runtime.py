"""JSON isolate for py_eval. Do not print user data."""

from __future__ import annotations

import ast
import builtins
import io
import json
import os
import sys

# Do not block os/ctypes/importlib/pathlib: numpy/pandas/Chaquopy need them.
# Deleting those from sys.modules made every eval raise a generic failed on device.
_BLOCKED_ROOTS = (
    "socket",
    "subprocess",
    "urllib",
    "http",
    "ftplib",
    "ssl",
    "webbrowser",
    "java",
    "android",
    "pty",
)

_OS_DENY = (
    "system",
    "popen",
    "remove",
    "removedirs",
    "rmdir",
    "unlink",
    "mkdir",
    "makedirs",
    "rename",
    "replace",
    "chmod",
    "chown",
    "chdir",
    "listdir",
    "scandir",
    "walk",
    "open",
    "execl",
    "execle",
    "execlp",
    "execv",
    "execve",
    "execvp",
    "execvpe",
    "spawnl",
    "spawnle",
    "spawnlp",
    "spawnlpe",
    "spawnv",
    "spawnve",
    "spawnvp",
    "spawnvpe",
    "startfile",
)

_locked = False


class _BlockedFinder:
    def find_spec(self, fullname, path, target=None):
        root = fullname.split(".", 1)[0]
        if root in _BLOCKED_ROOTS:
            raise ImportError("host")
        return None


def _blocked_open(*args, **kwargs):
    raise OSError("host")


def _lock_host():
    builtins.open = _blocked_open
    io.open = _blocked_open
    deny = _blocked_open
    for name in _OS_DENY:
        if hasattr(os, name):
            setattr(os, name, deny)
    if not any(isinstance(f, _BlockedFinder) for f in sys.meta_path):
        sys.meta_path.insert(0, _BlockedFinder())


def _ensure_locked():
    global _locked
    if _locked:
        return
    try:
        import numpy  # noqa: F401
        import pandas  # noqa: F401
    except Exception:
        raise RuntimeError("engine") from None
    _lock_host()
    _locked = True


def _to_json(value):
    if value is None:
        raise ValueError("no_value")

    def default(o):
        item = getattr(o, "item", None)
        if callable(item):
            return item()
        raise TypeError("no_value")

    try:
        return json.dumps(value, default=default)
    except TypeError:
        raise ValueError("no_value")


def evaluate(script, data_json):
    _ensure_locked()
    data = json.loads(data_json)
    tree = ast.parse(script, mode="exec")
    ns = {"data": data}
    sink = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    sys.stdout = sink
    sys.stderr = sink
    try:
        if not tree.body:
            raise ValueError("no_value")
        last = tree.body[-1]
        if not isinstance(last, ast.Expr):
            exec(compile(tree, "<py_eval>", "exec"), ns, ns)
            raise ValueError("no_value")
        prelude = tree.body[:-1]
        if prelude:
            exec(
                compile(ast.Module(prelude, type_ignores=[]), "<py_eval>", "exec"),
                ns,
                ns,
            )
        value = eval(
            compile(ast.Expression(last.value), "<py_eval>", "eval"),
            ns,
            ns,
        )
        return _to_json(value)
    except ImportError:
        raise RuntimeError("host") from None
    except OSError:
        raise RuntimeError("host") from None
    except ValueError as exc:
        if str(exc) == "no_value":
            raise RuntimeError("no_value") from None
        raise RuntimeError("failed") from None
    except SyntaxError:
        raise RuntimeError("failed") from None
    except RuntimeError as exc:
        if str(exc) in ("host", "no_value", "engine", "failed"):
            raise
        raise RuntimeError("failed") from None
    except Exception:
        raise RuntimeError("failed") from None
    finally:
        sys.stdout = old_out
        sys.stderr = old_err
