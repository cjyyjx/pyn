import os
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.label import Label
from kivy.uix.scrollview import ScrollView
from kivy.uix.gridlayout import GridLayout
from kivy.utils import get_color_from_hex
from kivy.clock import Clock
from kivy.core.window import Window

from main import STORAGE


class FileBrowserTab(BoxLayout):
    def __init__(self, **kw):
        super().__init__(orientation="vertical", spacing=2, padding=[4, 4, 4, 4], **kw)
        self.current_path = STORAGE

        # Path bar
        self.path_label = Label(
            text=self.current_path,
            size_hint_y=None,
            height=36,
            color=get_color_from_hex("#858585"),
            halign="left",
            valign="middle",
        )
        self.add_widget(self.path_label)

        # Toolbar
        toolbar = BoxLayout(size_hint_y=None, height=44, spacing=4)
        up_btn = Button(
            text="上级",
            size_hint_x=None,
            width=70,
            background_normal="",
            background_color=get_color_from_hex("#0e639c"),
            color="#ffffff",
        )
        up_btn.bind(on_press=self._go_up)
        toolbar.add_widget(up_btn)

        refresh_btn = Button(
            text="刷新",
            size_hint_x=None,
            width=70,
            background_normal="",
            background_color=get_color_from_hex("#0e639c"),
            color="#ffffff",
        )
        refresh_btn.bind(on_press=lambda x: self._list_dir())
        toolbar.add_widget(refresh_btn)

        newfile_btn = Button(
            text="新建文件",
            size_hint_x=None,
            width=90,
            background_normal="",
            background_color=get_color_from_hex("#0e639c"),
            color="#ffffff",
        )
        newfile_btn.bind(on_press=self._new_file)
        toolbar.add_widget(newfile_btn)

        newdir_btn = Button(
            text="新建目录",
            size_hint_x=None,
            width=90,
            background_normal="",
            background_color=get_color_from_hex("#0e639c"),
            color="#ffffff",
        )
        newdir_btn.bind(on_press=self._new_dir)
        toolbar.add_widget(newdir_btn)

        self.add_widget(toolbar)

        # File list
        self.file_list = GridLayout(cols=1, spacing=2, size_hint_y=None)
        self.file_list.bind(min_height=self.file_list.setter("height"))

        scroll = ScrollView()
        scroll.add_widget(self.file_list)
        self.add_widget(scroll)

        self._list_dir()

    def _list_dir(self):
        self.file_list.clear_widgets()
        self.path_label.text = self.current_path

        try:
            entries = sorted(os.listdir(self.current_path))
        except PermissionError:
            self.file_list.add_widget(Label(text="无权限访问", color=(1, 0, 0, 1)))
            return

        dirs = [e for e in entries if os.path.isdir(os.path.join(self.current_path, e))]
        files = [e for e in entries if os.path.isfile(os.path.join(self.current_path, e))]

        for name in dirs:
            btn = Button(
                text="📁 " + name,
                size_hint_y=None,
                height=40,
                background_normal="",
                background_color=get_color_from_hex("#2d2d2d"),
                color=get_color_from_hex("#d4d4d4"),
                halign="left",
                padding=[10, 0],
            )
            btn.bind(on_press=lambda x, n=name: self._enter_dir(n))
            self.file_list.add_widget(btn)

        for name in files:
            btn = Button(
                text="📄 " + name,
                size_hint_y=None,
                height=40,
                background_normal="",
                background_color=get_color_from_hex("#252526"),
                color=get_color_from_hex("#d4d4d4"),
                halign="left",
                padding=[10, 0],
            )
            btn.bind(on_press=lambda x, n=name: self._open_file(n))
            self.file_list.add_widget(btn)

    def _enter_dir(self, name):
        self.current_path = os.path.join(self.current_path, name)
        self._list_dir()

    def _go_up(self, btn):
        parent = os.path.dirname(self.current_path)
        if parent and parent != self.current_path:
            self.current_path = parent
            self._list_dir()

    def _open_file(self, name):
        path = os.path.join(self.current_path, name)
        try:
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
        except Exception as e:
            content = f"# 打开失败: {e}"

        from kivy.uix.popup import Popup
        from kivy.uix.textinput import TextInput as TI

        popup = Popup(
            title=name,
            content=TI(
                text=content,
                readonly=True,
                font_name="",
                font_size=13,
                foreground_color=get_color_from_hex("#d4d4d4"),
                background_color=get_color_from_hex("#1e1e1e"),
            ),
            size_hint=(0.9, 0.8),
        )
        popup.open()

    def _new_file(self, btn):
        from kivy.uix.popup import Popup
        from kivy.uix.textinput import TextInput as TI
        from kivy.uix.boxlayout import BoxLayout as BL
        from kivy.uix.button import Button as Btn

        content = BL(orientation="vertical", spacing=4)
        ti = TI(text="new_script.py", multiline=False, size_hint_y=None, height=40)
        content.add_widget(ti)

        def do_create(inst):
            name = ti.text.strip()
            if not name:
                return
            path = os.path.join(self.current_path, name)
            try:
                open(path, "w").close()
                popup.dismiss()
                self._list_dir()
            except Exception as e:
                ti.text = f"错误: {e}"

        bb = BL(size_hint_y=None, height=44, spacing=4)
        cb = Btn(text="取消")
        sb = Btn(text="创建")
        sb.bind(on_press=do_create)
        cb.bind(on_press=lambda x: popup.dismiss())
        bb.add_widget(cb)
        bb.add_widget(sb)
        content.add_widget(bb)

        popup = Popup(title="新建文件", content=content, size_hint=(0.8, 0.4))
        popup.open()

    def _new_dir(self, btn):
        from kivy.uix.popup import Popup
        from kivy.uix.textinput import TextInput as TI
        from kivy.uix.boxlayout import BoxLayout as BL
        from kivy.uix.button import Button as Btn

        content = BL(orientation="vertical", spacing=4)
        ti = TI(text="new_folder", multiline=False, size_hint_y=None, height=40)
        content.add_widget(ti)

        def do_create(inst):
            name = ti.text.strip()
            if not name:
                return
            path = os.path.join(self.current_path, name)
            try:
                os.makedirs(path, exist_ok=True)
                popup.dismiss()
                self._list_dir()
            except Exception as e:
                ti.text = f"错误: {e}"

        bb = BL(size_hint_y=None, height=44, spacing=4)
        cb = Btn(text="取消")
        sb = Btn(text="创建")
        sb.bind(on_press=do_create)
        cb.bind(on_press=lambda x: popup.dismiss())
        bb.add_widget(cb)
        bb.add_widget(sb)
        content.add_widget(bb)

        popup = Popup(title="新建目录", content=content, size_hint=(0.8, 0.4))
        popup.open()
