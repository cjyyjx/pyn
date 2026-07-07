import os, sys
from kivy import platform
from kivy.app import App
from kivy.uix.tabbedpanel import TabbedPanel, TabbedPanelHeader
from kivy.core.window import Window
from kivy.clock import Clock
from kivy.utils import get_color_from_hex

os.environ.setdefault("PYTHONDONTWRITEBYTECODE", "1")

if platform == "android":
    STORAGE = os.path.join(os.environ.get("ANDROID_PRIVATE", os.environ.get("EXTERNAL_STORAGE", "/sdcard")), "PyAndroid")
else:
    STORAGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "workspace")
os.makedirs(STORAGE, exist_ok=True)

from app.terminal import TerminalTab
from app.editor import EditorTab
from app.filebrowser import FileBrowserTab
from app.packages import PackageTab


class PyAndroid(App):
    def build(self):
        self.title = "PyAndroid"
        Window.clearcolor = get_color_from_hex("#1e1e1e")

        panel = TabbedPanel(do_default_tab=False, tab_width=Window.width / 4)
        panel.background_color = get_color_from_hex("#1e1e1e")
        panel.background_image = ""

        for tab_class, label in [
            (TerminalTab, "终端"),
            (EditorTab, "编辑器"),
            (FileBrowserTab, "文件"),
            (PackageTab, "包管理"),
        ]:
            header = TabbedPanelHeader(text=label)
            header.content = tab_class()
            header.background_color = get_color_from_hex("#2d2d2d")
            header.background_down = get_color_from_hex("#3e3e3e")
            panel.add_widget(header)

        self.panel = panel
        return panel

    def on_pause(self):
        return True


if __name__ == "__main__":
    PyAndroid().run()
