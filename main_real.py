import os, sys, traceback

CRASH_LOG = os.path.join(os.environ.get("EXTERNAL_STORAGE", "/sdcard"), "pyandroid_crash.txt")

def _log_crash(exc):
    try:
        with open(CRASH_LOG, "w") as f:
            f.write(traceback.format_exc())
    except:
        pass

try:
    from kivy import platform
    from kivy.app import App
    from kivy.uix.tabbedpanel import TabbedPanel, TabbedPanelHeader
    from kivy.core.window import Window
    from kivy.utils import get_color_from_hex
    from kivy.uix.label import Label

    os.environ.setdefault("PYTHONDONTWRITEBYTECODE", "1")

    if platform == "android":
        STORAGE = os.path.join(os.environ.get("ANDROID_PRIVATE", "/sdcard"), "PyAndroid")
    else:
        STORAGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "workspace")

    from app.terminal import TerminalTab
    from app.editor import EditorTab
    from app.filebrowser import FileBrowserTab
    from app.packages import PackageTab


    class PyAndroid(App):
        def build(self):
            self.title = "PyAndroid"
            Window.clearcolor = get_color_from_hex("#1e1e1e")
            try:
                os.makedirs(STORAGE, exist_ok=True)
                tab_w = max(Window.size[0] / 4, 100)
                panel = TabbedPanel(do_default_tab=False, tab_width=tab_w)
                panel.background_color = get_color_from_hex("#1e1e1e")

                for tab_class, label in [
                    (TerminalTab, "终端"),
                    (EditorTab, "编辑器"),
                    (FileBrowserTab, "文件"),
                    (PackageTab, "包管理"),
                ]:
                    header = TabbedPanelHeader(text=label)
                    header.content = tab_class()
                    header.background_color = get_color_from_hex("#2d2d2d")
                    panel.add_widget(header)
                return panel
            except Exception:
                _log_crash(sys.exc_info()[1])
                return Label(text="启动失败，请查看 /sdcard/pyandroid_crash.txt")

        def on_pause(self):
            return True

    if __name__ == "__main__":
        PyAndroid().run()

except Exception:
    _log_crash(sys.exc_info()[1])
    raise
