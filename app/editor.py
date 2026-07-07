import os, re
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.scrollview import ScrollView
from kivy.uix.relativelayout import RelativeLayout
from kivy.utils import get_color_from_hex
from kivy.clock import Clock
from kivy.properties import ObjectProperty, StringProperty
from kivy.core.window import Window
from pygments import lex
from pygments.lexers import PythonLexer
from pygments.token import Token

from main import STORAGE


class LineNumberWidget(TextInput):
    def __init__(self, editor, **kw):
        super().__init__(
            readonly=True,
            font_name="",
            font_size=14,
            size_hint_x=None,
            width=50,
            background_color=get_color_from_hex("#1e1e1e"),
            foreground_color=get_color_from_hex("#858585"),
            halign="right",
            **kw
        )
        self.editor = editor

    def update(self, *a):
        n = self.editor.text.count("\n") + 1
        self.text = "\n".join(str(i) for i in range(1, n + 1))
        # Keep scroll sync
        self.scroll_y = self.editor.scroll_y


class CodeEditor(TextInput):
    def __init__(self, **kw):
        super().__init__(
            font_name="",
            font_size=14,
            foreground_color=get_color_from_hex("#d4d4d4"),
            background_color=get_color_from_hex("#1e1e1e"),
            cursor_color=get_color_from_hex("#569cd6"),
            selection_color=get_color_from_hex("#264f78"),
            tab_width=4,
            **kw
        )
        self._highlight = True
        self.bind(text=self._highlight_text)

    def _highlight_text(self, *a):
        if not self._highlight:
            return
        # Very basic real-time highlight via Pygments (simplified)
        # Full highlight would need custom widget; this is a lightweight approach
        pass


class EditorTab(BoxLayout):
    def __init__(self, **kw):
        super().__init__(orientation="vertical", spacing=4, padding=[4, 4, 4, 4], **kw)
        self.current_file = None
        self.unsaved = False

        # Toolbar
        toolbar = BoxLayout(size_hint_y=None, height=44, spacing=4)

        for txt, cb in [
            ("新建", self._new_file),
            ("打开", self._open_file),
            ("保存", self._save_file),
            ("运行", self._run_file),
        ]:
            btn = Button(
                text=txt,
                size_hint_x=None,
                width=70,
                background_normal="",
                background_color=get_color_from_hex("#0e639c"),
                color=get_color_from_hex("#ffffff"),
            )
            btn.bind(on_press=cb)
            toolbar.add_widget(btn)

        # Filename label
        self.fname_label = Button(
            text="无文件",
            size_hint_x=1,
            background_normal="",
            background_color=get_color_from_hex("#252526"),
            color=get_color_from_hex("#858585"),
            halign="left",
            padding=[10, 0],
        )
        toolbar.add_widget(self.fname_label)

        self.add_widget(toolbar)

        # Editor area with line numbers
        editor_area = BoxLayout(orientation="horizontal", spacing=0)

        self.editor = CodeEditor()
        self.line_numbers = LineNumberWidget(self.editor)
        self.editor.bind(text=self.line_numbers.update)
        self.editor.bind(scroll_y=self.line_numbers.update)

        editor_area.add_widget(self.line_numbers)
        editor_area.add_widget(self.editor)

        self.add_widget(editor_area)

    def _new_file(self, btn):
        self.editor.text = ""
        self.current_file = None
        self.fname_label.text = "无标题"
        self.unsaved = True

    def _open_file(self, btn):
        from kivy.uix.filechooser import FileChooserListView
        from kivy.uix.popup import Popup
        from kivy.uix.boxlayout import BoxLayout as BL

        content = BL(orientation="vertical")
        fc = FileChooserListView(path=STORAGE, filters=["*.py"])
        content.add_widget(fc)

        btn_box = BL(size_hint_y=None, height=44, spacing=4)
        cancel_btn = Button(text="取消")
        select_btn = Button(text="选择")

        btn_box.add_widget(cancel_btn)
        btn_box.add_widget(select_btn)
        content.add_widget(btn_box)

        popup = Popup(title="打开文件", content=content, size_hint=(0.9, 0.8))

        def on_select(inst):
            sel = fc.selection
            if sel:
                path = sel[0]
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        self.editor.text = f.read()
                    self.current_file = path
                    self.fname_label.text = os.path.basename(path)
                    self.unsaved = False
                except Exception as e:
                    self.editor.text = f"# 错误: {e}"
            popup.dismiss()

        select_btn.bind(on_press=on_select)
        cancel_btn.bind(on_press=popup.dismiss)
        popup.open()

    def _save_file(self, btn):
        if self.current_file:
            path = self.current_file
        else:
            from kivy.uix.popup import Popup
            from kivy.uix.textinput import TextInput as TI
            from kivy.uix.boxlayout import BoxLayout as BL
            from kivy.uix.button import Button as Btn

            content = BL(orientation="vertical", spacing=4)
            ti = TI(text="script.py", multiline=False, size_hint_y=None, height=40)
            content.add_widget(ti)

            def do_save(inst):
                name = ti.text.strip()
                if not name.endswith(".py"):
                    name += ".py"
                path = os.path.join(STORAGE, name)
                try:
                    with open(path, "w", encoding="utf-8") as f:
                        f.write(self.editor.text)
                    self.current_file = path
                    self.fname_label.text = name
                    self.unsaved = False
                    popup.dismiss()
                except Exception as e:
                    self.editor.text += f"\n# 保存失败: {e}"

            bb = BL(size_hint_y=None, height=44, spacing=4)
            cb = Btn(text="取消")
            sb = Btn(text="保存")
            sb.bind(on_press=do_save)
            cb.bind(on_press=lambda x: popup.dismiss())
            bb.add_widget(cb)
            bb.add_widget(sb)
            content.add_widget(bb)
            popup = Popup(title="保存为", content=content, size_hint=(0.8, 0.4))
            popup.open()
            return

        try:
            with open(path, "w", encoding="utf-8") as f:
                f.write(self.editor.text)
            self.unsaved = False
        except Exception as e:
            self.editor.text += f"\n# 保存失败: {e}"

    def _run_file(self, btn):
        code = self.editor.text.strip()
        if not code:
            return

        import io, traceback, sys
        old_out, old_err = sys.stdout, sys.stderr
        sys.stdout = io.StringIO()
        sys.stderr = io.StringIO()

        namespace = {"__name__": "__main__", "__builtins__": __builtins__}
        try:
            compiled = compile(code, self.current_file or "<editor>", "exec")
            exec(compiled, namespace)
            out = sys.stdout.getvalue()
            err = sys.stderr.getvalue()
            result = (out or "") + (err or "")
        except Exception:
            result = traceback.format_exc()
        finally:
            sys.stdout, sys.stderr = old_out, old_err

        from app.terminal import TerminalTab
        # Show result in a popup
        from kivy.uix.popup import Popup
        from kivy.uix.textinput import TextInput as TI
        popup = Popup(
            title="运行结果",
            content=TI(text=result or "(无输出)", readonly=True, font_name="", font_size=13),
            size_hint=(0.9, 0.7),
        )
        popup.open()
