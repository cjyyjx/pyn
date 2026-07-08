import sys
import io
import importlib
import importlib.util
import zipfile
import json
import os.path

def install_package(package_name: str) -> str:
    try:
        import pip
        old_out = sys.stdout
        old_err = sys.stderr
        sys.stdout = io.StringIO()
        sys.stderr = io.StringIO()
        try:
            retcode = pip.main(["install", package_name])
            out = sys.stdout.getvalue()
            err = sys.stderr.getvalue()
            if retcode == 0:
                return f"OK: {package_name} installed\n{out}"
            else:
                return f"FAILED ({retcode}): {err or out}"
        finally:
            sys.stdout = old_out
            sys.stderr = old_err
    except ImportError:
        return "FAILED: pip not available"

def list_installed() -> str:
    try:
        import pip._internal as pip_internal
        from pip._internal.commands.list import ListCommand
        cmd = ListCommand()
        opts, args = cmd.parse_args([])
        packages = cmd.run(opts, args)
        result = []
        for pkg in packages:
            result.append(f"{pkg.name}=={pkg.version}")
        return "\n".join(result) if result else "(no packages)"
    except Exception as e:
        return f"Error: {e}"

def uninstall_package(package_name: str) -> str:
    try:
        import pip
        old_out = sys.stdout
        old_err = sys.stderr
        sys.stdout = io.StringIO()
        sys.stderr = io.StringIO()
        try:
            retcode = pip.main(["uninstall", "-y", package_name])
            out = sys.stdout.getvalue()
            err = sys.stderr.getvalue()
            if retcode == 0:
                return f"OK: {package_name} uninstalled\n{out}"
            else:
                return f"FAILED ({retcode}): {err or out}"
        finally:
            sys.stdout = old_out
            sys.stderr = old_err
    except ImportError:
        return "FAILED: pip not available"
