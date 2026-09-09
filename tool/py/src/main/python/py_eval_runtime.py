"""JSON isolate for py_eval. Do not print user data or file bytes."""

from __future__ import annotations

import ast
import builtins
import io
import json
import os
import sys

# Do not delete os/ctypes/importlib from sys.modules (numpy/Chaquopy need them).
# ctypes stays blocked for *new* imports via finder after numpy is loaded.
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
    "ctypes",
)

_OS_DENY = (
    "system",
    "popen",
    "removedirs",
    "rmdir",
    "unlink",
    "makedirs",
    "replace",
    "chmod",
    "chown",
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

_QUOTA = 32 * 1024 * 1024
_real_open = builtins.open
_real_listdir = os.listdir
_real_mkdir = os.mkdir
_real_remove = os.remove
_real_rename = os.rename
_real_chdir = os.chdir
_real_getcwd = os.getcwd
_real_walk = os.walk
_real_scandir = os.scandir
_real_getsize = os.path.getsize
_real_isdir = os.path.isdir
_real_isfile = os.path.isfile
_real_islink = os.path.islink

_locked = False
_sandbox_root = None


class _BlockedFinder:
    def find_spec(self, fullname, path, target=None):
        root = fullname.split(".", 1)[0]
        if root in _BLOCKED_ROOTS:
            raise ImportError("host")
        return None


def _blocked(*args, **kwargs):
    raise RuntimeError("host")


def _require_root():
    root = _sandbox_root
    if not root:
        raise RuntimeError("host")
    return root


def _jail_path(user_path):
    root = _require_root()
    if isinstance(user_path, bytes):
        user_path = os.fsdecode(user_path)
    path = os.fspath(user_path)
    if not isinstance(path, str) or not path:
        raise RuntimeError("host")
    if "://" in path or "\x00" in path:
        raise RuntimeError("host")
    lowered = path.lower()
    if lowered.startswith("content:") or lowered.startswith("file:"):
        raise RuntimeError("host")
    candidate = path if os.path.isabs(path) else os.path.join(root, path)
    resolved = os.path.realpath(candidate)
    if not (resolved == root or resolved.startswith(root + os.sep)):
        raise RuntimeError("host")
    return resolved


def _dir_size(root):
    total = 0
    stack = [root]
    while stack:
        current = stack.pop()
        try:
            names = _real_listdir(current)
        except OSError:
            continue
        for name in names:
            path = os.path.join(current, name)
            try:
                if _real_islink(path):
                    continue
                if _real_isdir(path):
                    stack.append(path)
                elif _real_isfile(path):
                    total += _real_getsize(path)
            except OSError:
                pass
    return total


def _is_write_mode(mode):
    if mode is None:
        return False
    text = os.fsdecode(mode) if isinstance(mode, bytes) else str(mode)
    return any(ch in text for ch in "wax+")


class _QuotaFile:
    def __init__(self, inner, root):
        object.__setattr__(self, "_inner", inner)
        object.__setattr__(self, "_root", root)

    def write(self, data):
        n = len(data) if isinstance(data, (str, bytes, bytearray)) else 0
        if _dir_size(self._root) + n > _QUOTA:
            raise RuntimeError("quota")
        return self._inner.write(data)

    def writelines(self, lines):
        for line in lines:
            self.write(line)

    def fileno(self):
        raise RuntimeError("host")

    def detach(self):
        raise RuntimeError("host")

    def __enter__(self):
        self._inner.__enter__()
        return self

    def __exit__(self, *exc):
        return self._inner.__exit__(*exc)

    def __iter__(self):
        return iter(self._inner)

    def __getattr__(self, name):
        return getattr(self._inner, name)


def _jailed_open(*args, **kwargs):
    if args:
        file = args[0]
        rest = args[1:]
        mode = rest[0] if rest else kwargs.get("mode", "r")
        resolved = _jail_path(file)
        handle = _real_open(resolved, *rest, **kwargs)
    else:
        file = kwargs.get("file")
        if file is None:
            raise RuntimeError("host")
        mode = kwargs.get("mode", "r")
        kwargs = dict(kwargs)
        kwargs["file"] = _jail_path(file)
        handle = _real_open(**kwargs)
    if _is_write_mode(mode):
        return _QuotaFile(handle, _require_root())
    return handle


def _jailed_listdir(path="."):
    return _real_listdir(_jail_path(path))


def _jailed_mkdir(path, mode=0o777, *, dir_fd=None):
    if dir_fd is not None:
        raise RuntimeError("host")
    return _real_mkdir(_jail_path(path), mode)


def _jailed_remove(path, *, dir_fd=None):
    if dir_fd is not None:
        raise RuntimeError("host")
    return _real_remove(_jail_path(path))


def _jailed_rename(src, dst, *, src_dir_fd=None, dst_dir_fd=None):
    if src_dir_fd is not None or dst_dir_fd is not None:
        raise RuntimeError("host")
    return _real_rename(_jail_path(src), _jail_path(dst))


def _jailed_chdir(path):
    return _real_chdir(_jail_path(path))


def _jailed_scandir(path="."):
    return _real_scandir(_jail_path(path))


def _jailed_walk(top=".", *args, **kwargs):
    return _real_walk(_jail_path(top), *args, **kwargs)


def _lock_host():
    builtins.open = _jailed_open
    io.open = _jailed_open
    for name in _OS_DENY:
        if hasattr(os, name):
            setattr(os, name, _blocked)
    os.listdir = _jailed_listdir
    os.mkdir = _jailed_mkdir
    os.remove = _jailed_remove
    os.rename = _jailed_rename
    os.chdir = _jailed_chdir
    os.scandir = _jailed_scandir
    os.walk = _jailed_walk
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


def evaluate(script, data_json, sandbox_root):
    global _sandbox_root
    _ensure_locked()
    data = json.loads(data_json)
    root = os.path.realpath(sandbox_root)
    if not root or not os.path.isdir(root):
        raise RuntimeError("host")
    tree = ast.parse(script, mode="exec")
    ns = {"data": data}
    sink = io.StringIO()
    old_out, old_err = sys.stdout, sys.stderr
    old_cwd = _real_getcwd()
    sys.stdout = sink
    sys.stderr = sink
    _sandbox_root = root
    try:
        _real_chdir(root)
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
    except ValueError as exc:
        if str(exc) == "no_value":
            raise RuntimeError("no_value") from None
        raise RuntimeError("failed") from None
    except SyntaxError:
        raise RuntimeError("failed") from None
    except RuntimeError as exc:
        if str(exc) in ("host", "no_value", "engine", "failed", "quota"):
            raise
        raise RuntimeError("failed") from None
    except Exception:
        raise RuntimeError("failed") from None
    finally:
        _sandbox_root = None
        try:
            _real_chdir(old_cwd)
        except OSError:
            pass
        sys.stdout = old_out
        sys.stderr = old_err
