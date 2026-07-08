import os, sys, traceback
from kivy import platform
from kivy.app import App
from kivy.uix.label import Label
from kivy.core.window import Window
from kivy.utils import get_color_from_hex

os.environ.setdefault("PYTHONDONTWRITEBYTECODE", "1")

if platform == "android":
    STORAGE = os.path.join(os.environ.get("ANDROID_PRIVATE", os.environ.get("EXTERNAL_STORAGE", "/sdcard")), "PyAndroid")
else:
    STORAGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "workspace")
os.makedirs(STORAGE, exist_ok=True)

STEPS = []

def step(msg):
    STEPS.append(msg)

def try_import(module, cls_name):
    try:
        mod = __import__(module, fromlist=[cls_name])
        cls = getattr(mod, cls_name)
        step(f"OK import {cls_name}")
        return cls
    except Exception as e:
        step(f"FAIL import {cls_name}: {e}")
        return None

TerminalTab = try_import("app.terminal", "TerminalTab")
EditorTab = try_import("app.editor", "EditorTab")
FileBrowserTab = try_import("app.filebrowser", "FileBrowserTab")
PackageTab = try_import("app.packages", "PackageTab")

def try_instantiate(cls, name):
    if cls is None:
        step(f"SKIP instantiate {name} (import failed)")
        return None
    try:
        obj = cls()
        step(f"OK instantiate {name}")
        return obj
    except Exception as e:
        step(f"FAIL instantiate {name}: {traceback.format_exc()}")
        return None

term = try_instantiate(TerminalTab, "TerminalTab")
editor = try_instantiate(EditorTab, "EditorTab")
fb = try_instantiate(FileBrowserTab, "FileBrowserTab")
pkg = try_instantiate(PackageTab, "PackageTab")

class PyAndroid(App):
    def build(self):
        self.title = "PyAndroid"
        Window.clearcolor = get_color_from_hex("#1e1e1e")
        text = "\n".join(STEPS)
        if any(s.startswith("FAIL") for s in STEPS):
            return Label(text=text, color=(1,0,0,1), font_size=14)
        return Label(text=text + "\n\nAll OK, trying TabbedPanel...", color=(1,1,1,1))

    def on_pause(self):
        return True

if __name__ == "__main__":
    PyAndroid().run()
