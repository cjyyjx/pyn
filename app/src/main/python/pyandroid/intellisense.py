import sys
import ast
import inspect
import builtins
import keyword
import json
import os.path

PYTHON_KEYWORDS = keyword.kwlist

BUILTIN_NAMES = [name for name in dir(builtins) if not name.startswith("_")]

BUILTIN_FUNCTIONS = [name for name in BUILTIN_NAMES
                     if callable(getattr(builtins, name, None))]

BUILTIN_TYPES = [name for name in BUILTIN_NAMES
                 if isinstance(getattr(builtins, name, None), type)]

import sys as _sys
COMPLETION_PRIORITY = {
    "keyword": 10,
    "builtin": 8,
    "function": 7,
    "class": 7,
    "variable": 6,
    "module": 6,
    "snippet": 9,
}

def get_completions(code: str, cursor_line: int, cursor_col: int) -> str:
    lines = code.split("\n")
    if cursor_line >= len(lines):
        cursor_line = len(lines) - 1
    current_line = lines[cursor_line][:cursor_col]

    prefix = ""
    word_start = cursor_col
    while word_start > 0 and (current_line[word_start-1].isalnum() or current_line[word_start-1] == "_"):
        word_start -= 1
    prefix = current_line[word_start:cursor_col]

    if not prefix and cursor_col > 0 and current_line[cursor_col-1:].startswith("."):
        prefix = "."

    completions = []
    if prefix:
        for kw in PYTHON_KEYWORDS:
            if kw.startswith(prefix):
                completions.append({"label": kw, "type": "keyword", "detail": "keyword"})
        for name in BUILTIN_NAMES:
            if name.startswith(prefix):
                ctype = "builtin"
                if name in BUILTIN_FUNCTIONS:
                    ctype = "function"
                elif name in BUILTIN_TYPES:
                    ctype = "class"
                completions.append({"label": name, "type": ctype, "detail": "built-in"})

    snippet_completions = [
        {"label": "if__", "type": "snippet", "detail": "if statement", "insert": "if :\n    "},
        {"label": "for__", "type": "snippet", "detail": "for loop", "insert": "for  in :\n    "},
        {"label": "while__", "type": "snippet", "detail": "while loop", "insert": "while :\n    "},
        {"label": "def__", "type": "snippet", "detail": "function definition", "insert": "def ():\n    "},
        {"label": "class__", "type": "snippet", "detail": "class definition", "insert": "class :\n    "},
        {"label": "try__", "type": "snippet", "detail": "try/except", "insert": "try:\n    \nexcept  as e:\n    "},
        {"label": "main__", "type": "snippet", "detail": "if __name__ guard", "insert": 'if __name__ == "__main__":\n    '},
    ]
    for s in snippet_completions:
        label = s["label"].replace("__", " " + prefix)
        if s["label"].startswith("__") or s["label"].startswith(prefix):
            completions.append(s)

    try:
        tree = ast.parse(code)
        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef):
                if node.name.startswith(prefix):
                    args = [a.arg for a in node.args.args]
                    completions.append({"label": node.name, "type": "function", "detail": f"def({', '.join(args)})"})
            elif isinstance(node, ast.ClassDef):
                if node.name.startswith(prefix):
                    completions.append({"label": node.name, "type": "class", "detail": "class"})
            elif isinstance(node, ast.Assign):
                for target in node.targets:
                    if isinstance(target, ast.Name) and target.id.startswith(prefix):
                        completions.append({"label": target.id, "type": "variable", "detail": "variable"})
    except SyntaxError:
        pass

    seen = set()
    unique = []
    for c in completions:
        key = c["label"]
        if key not in seen:
            seen.add(key)
            unique.append(c)
    unique.sort(key=lambda x: (COMPLETION_PRIORITY.get(x["type"], 5), x["label"]))
    return json.dumps({"prefix": prefix, "completions": unique[:30]})

def check_syntax(code: str) -> str:
    try:
        ast.parse(code)
        return json.dumps({"valid": True, "errors": []})
    except SyntaxError as e:
        return json.dumps({
            "valid": False,
            "errors": [{
                "line": e.lineno or 0,
                "col": e.offset or 0,
                "message": e.msg,
                "text": e.text or ""
            }]
        })

def format_code(code: str) -> str:
    try:
        import ast as _ast
        tree = _ast.parse(code)
        import _ast as _ast_module
        result = []
        for node in tree.body:
            result.append(_format_node(node, 0))
        return "\n".join(result)
    except SyntaxError:
        return code

def _format_node(node, indent):
    import ast as _ast
    ind = "    " * indent
    if isinstance(node, _ast.FunctionDef):
        args = ", ".join(a.arg for a in node.args.args)
        body = "\n".join(_format_node(n, indent + 1) for n in node.body)
        return f"{ind}def {node.name}({args}):\n{body}"
    elif isinstance(node, _ast.ClassDef):
        body = "\n".join(_format_node(n, indent + 1) for n in node.body)
        return f"{ind}class {node.name}:\n{body}"
    elif isinstance(node, _ast.Expr):
        return f"{ind}{_ast.unparse(node)}"
    elif isinstance(node, _ast.Return):
        return f"{ind}return {_ast.unparse(node.value) if node.value else ''}"
    elif isinstance(node, _ast.Assign):
        targets = ", ".join(_ast.unparse(t) for t in node.targets)
        return f"{ind}{targets} = {_ast.unparse(node.value)}"
    elif isinstance(node, _ast.If):
        test = _ast.unparse(node.test)
        body = "\n".join(_format_node(n, indent + 1) for n in node.body)
        result = f"{ind}if {test}:\n{body}"
        if node.orelse:
            orelse = "\n".join(_format_node(n, indent + 1) for n in node.orelse)
            result += f"\n{ind}else:\n{orelse}"
        return result
    else:
        try:
            return f"{ind}{_ast.unparse(node)}"
        except:
            return f"{ind}# <unprintable node>"
