import subprocess, threading, sys, os, textwrap, importlib

from kivy.uix.boxlayout import BoxLayout
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.utils import get_color_from_hex
from kivy.clock import Clock
from kivy.core.window import Window
from kivy.uix.popup import Popup


class PackageTab(BoxLayout):
    def __init__(self, **kw):
        super().__init__(orientation="vertical", spacing=4, padding=[4, 4, 4, 4], **kw)

        # Header
        self.add_widget(Label(
            text="pip 包管理器 — 安装纯 Python 包",
            size_hint_y=None,
            height=36,
            color=get_color_from_hex("#858585"),
            halign="center",
        ))

        # Input area
        input_box = BoxLayout(size_hint_y=None, height=44, spacing=4)

        self.pkg_input = TextInput(
            text="",
            hint_text="输入包名...",
            multiline=False,
            font_name="",
            font_size=14,
            foreground_color=get_color_from_hex("#d4d4d4"),
            background_color=get_color_from_hex("#252526"),
            cursor_color=get_color_from_hex("#569cd6"),
        )

        install_btn = Button(
            text="安装",
            size_hint_x=None,
            width=80,
            background_normal="",
            background_color=get_color_from_hex("#0e639c"),
            color="#ffffff",
        )
        install_btn.bind(on_press=self._install_pkg)

        uninstall_btn = Button(
            text="卸载",
            size_hint_x=None,
            width=80,
            background_normal="",
            background_color=get_color_from_hex("#c04040"),
            color="#ffffff",
        )
        uninstall_btn.bind(on_press=self._uninstall_pkg)

        input_box.add_widget(self.pkg_input)
        input_box.add_widget(install_btn)
        input_box.add_widget(uninstall_btn)
        self.add_widget(input_box)

        # Output area
        self.output = TextInput(
            readonly=True,
            font_name="",
            font_size=13,
            foreground_color=get_color_from_hex("#d4d4d4"),
            background_color=get_color_from_hex("#1e1e1e"),
        )
        self.add_widget(self.output)

        # Quick install buttons
        quick_box = BoxLayout(size_hint_y=None, height=44, spacing=4)

        common_pkgs = ["requests", "flask", "numpy", "pillow", "beautifulsoup4"]
        for pkg in common_pkgs:
            btn = Button(
                text=pkg,
                size_hint_x=None,
                width=100,
                background_normal="",
                background_color=get_color_from_hex("#2d2d2d"),
                color=get_color_from_hex("#d4d4d4"),
                font_size=11,
            )
            btn.bind(on_press=lambda x, p=pkg: setattr(self.pkg_input, "text", p))
            quick_box.add_widget(btn)

        self.add_widget(quick_box)

    def _log(self, msg):
        Clock.schedule_once(lambda dt: setattr(self.output, "text", self.output.text + msg + "\n"))

    def _install_pkg(self, btn):
        pkg = self.pkg_input.text.strip()
        if not pkg:
            return
        self.output.text = f"正在安装 {pkg}...\n"
        threading.Thread(target=self._pip_install, args=(pkg,), daemon=True).start()

    def _uninstall_pkg(self, btn):
        pkg = self.pkg_input.text.strip()
        if not pkg:
            return
        self.output.text = f"正在卸载 {pkg}...\n"
        threading.Thread(target=self._pip_uninstall, args=(pkg,), daemon=True).start()

    def _pip_install(self, pkg):
        try:
            result = subprocess.run(
                [sys.executable, "-m", "pip", "install", pkg],
                capture_output=True, text=True, timeout=120
            )
            out = result.stdout + result.stderr
            Clock.schedule_once(lambda dt: setattr(self.output, "text", out))
        except subprocess.TimeoutExpired:
            Clock.schedule_once(lambda dt: setattr(self.output, "text", "超时: 安装时间过长\n"))
        except Exception as e:
            Clock.schedule_once(lambda dt: setattr(self.output, "text", f"错误: {e}\n"))

    def _pip_uninstall(self, pkg):
        try:
            result = subprocess.run(
                [sys.executable, "-m", "pip", "uninstall", "-y", pkg],
                capture_output=True, text=True, timeout=30
            )
            out = result.stdout + result.stderr
            Clock.schedule_once(lambda dt: setattr(self.output, "text", out))
        except Exception as e:
            Clock.schedule_once(lambda dt: setattr(self.output, "text", f"错误: {e}\n"))
