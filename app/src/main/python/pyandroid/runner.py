import sys
import io
import traceback
import contextlib

_NAMESPACE = {"__name__": "__main__", "__builtins__": __builtins__}

def execute(code: str) -> str:
    old_out = sys.stdout
    old_err = sys.stderr
    sys.stdout = io.StringIO()
    sys.stderr = io.StringIO()
    try:
        compiled = compile(code.strip(), "<exec>", "exec")
        exec(compiled, _NAMESPACE)
        out = sys.stdout.getvalue()
        err = sys.stderr.getvalue()
        return (out or "") + ("\n" + err if err else "")
    except Exception:
        _NAMESPACE.pop("__exception__", None)
        return traceback.format_exc()
    finally:
        sys.stdout = old_out
        sys.stderr = old_err

def execute_file(path: str) -> str:
    try:
        with open(path, "r", encoding="utf-8") as f:
            code = f.read()
        return execute(code)
    except Exception as e:
        return traceback.format_exc()

def get_globals() -> str:
    import json
    safe = {k: repr(type(v).__name__) for k, v in _NAMESPACE.items()
            if not k.startswith("_") and not callable(v) and not isinstance(v, type)}
    return json.dumps(safe)

def reset_namespace() -> str:
    _NAMESPACE.clear()
    _NAMESPACE.update({"__name__": "__main__", "__builtins__": __builtins__})
    return "Namespace cleared"
