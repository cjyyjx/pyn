import sys, io, traceback

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
        return (out or "") + (err or "")
    except Exception:
        return traceback.format_exc()
    finally:
        sys.stdout = old_out
        sys.stderr = old_err
