import os, sys, traceback
from kivy.app import App
from kivy.uix.label import Label
from kivy.core.window import Window

ERRORS = []

def _imp():
    for name, path in [
        ("TerminalTab", "app.terminal"),
        ("EditorTab", "app.editor"),
        ("FileBrowserTab", "app.filebrowser"),
        ("PackageTab", "app.packages"),
    ]:
        try:
            imp = __import__(path, fromlist=[name])
            globals()[name] = getattr(imp, name)
            ERRORS.append(f"OK {name}")
        except Exception as e:
            ERRORS.append(f"FAIL {name}: {e}")
_imp()

from kivy.uix.tabbedpanel import TabbedPanel, TabbedPanelHeader
from kivy.utils import get_color_from_hex

class PyAndroid(App):
    def build(self):
        self.title = "PyAndroid"
        Window.clearcolor = get_color_from_hex("#1e1e1e")
        err = "\n".join(ERRORS)
        if "FAIL" in err:
            return Label(text=err, color=(1,0,0,1))
        return Label(text="All imports OK", color=(1,1,1,1))

    def on_pause(self):
        return True

if __name__ == "__main__":
    PyAndroid().run()
