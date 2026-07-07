import sys, io, textwrap, traceback
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.scrollview import ScrollView
from kivy.uix.textinput import TextInput
from kivy.uix.label import Label
from kivy.clock import Clock
from kivy.utils import get_color_from_hex
from kivy.core.window import Window
from kivy.animation import Animation


class TerminalTab(BoxLayout):
    PS1 = ">>> "
    PS2 = "... "

    def __init__(self, **kw):
        super().__init__(orientation="vertical", spacing=0, padding=0, **kw)
        self._namespace = {"__name__": "__main__", "__builtins__": __builtins__}
        self._multiline = False
        self._multiline_buffer = ""
        self._history = []
        self._history_index = -1

        self.output = TextInput(
            readonly=True,
            text=self.PS1,
            font_name="",
            font_size=15,
            foreground_color=get_color_from_hex("#d4d4d4"),
            background_color=get_color_from_hex("#1e1e1e"),
            cursor_color=get_color_from_hex("#569cd6"),
            selection_color=get_color_from_hex("#264f78"),
            size_hint_y=0.75,
        )

        scroll = ScrollView(size_hint_y=0.75)
        scroll.add_widget(self.output)
        self.output.bind(min_height=scroll.setter("height"))

        inp_box = BoxLayout(size_hint_y=None, height=48, spacing=0)

        self.prompt_label = Label(
            text=self.PS1,
            size_hint_x=None,
            width=50,
            color=get_color_from_hex("#569cd6"),
            valign="center",
            halign="right",
        )

        self.input = TextInput(
            multiline=False,
            font_name="",
            font_size=15,
            foreground_color=get_color_from_hex("#d4d4d4"),
            background_color=get_color_from_hex("#252526"),
            cursor_color=get_color_from_hex("#569cd6"),
            selection_color=get_color_from_hex("#264f78"),
            size_hint_x=1,
        )
        self.input.bind(on_text_validate=self._on_enter)
        # Re-focus on touch
        self.input.focused = True

        inp_box.add_widget(self.prompt_label)
        inp_box.add_widget(self.input)

        self.add_widget(scroll)
        self.add_widget(inp_box)

        Clock.schedule_once(lambda dt: self.input.focus, 0.3)

    def _append_output(self, text):
        self.output.text += text
        # Auto-scroll to bottom
        self.output.cursor = self.output.cursor  # force update

    def _on_enter(self, instance):
        raw = self.input.text
        self.input.text = ""
        self._history.append(raw)
        self._history_index = len(self._history)

        text = raw
        self._append_output(text + "\n")

        if self._multiline:
            self._multiline_buffer += text + "\n"
            # Check if block is complete (dedent to same level or empty line)
            if text.strip() == "" or self._is_complete(self._multiline_buffer):
                code = self._multiline_buffer
                self._multiline = False
                self._multiline_buffer = ""
                self.prompt_label.text = self.PS1
                self._exec_code(code)
            else:
                self._append_output(self.PS2)
            return

        if text.strip() == "":
            self._append_output(self.PS1)
            return

        # Multiline blocks (if/def/class/for/while/try/with)
        if self._is_block_start(text):
            self._multiline = True
            self._multiline_buffer = text + "\n"
            self.prompt_label.text = self.PS2
            self._append_output(self.PS2)
            return

        self._exec_code(text)

    def _is_block_start(self, line):
        stripped = line.strip()
        keywords = ("if ", "elif ", "else:", "for ", "while ", "def ", "class ", "try:", "except",
                    "finally:", "with ", "async ")
        return any(stripped.startswith(k) for k in keywords)

    def _is_complete(self, code):
        try:
            compile(code, "<repl>", "exec")
            return True
        except SyntaxError as e:
            if "unexpected EOF" in str(e) or "incomplete" in str(e):
                return False
            return True

    def _exec_code(self, code):
        old_stdout = sys.stdout
        old_stderr = sys.stderr
        sys.stdout = io.StringIO()
        sys.stderr = io.StringIO()

        try:
            compiled = compile(code.strip(), "<repl>", "exec")
            exec(compiled, self._namespace)
            out = sys.stdout.getvalue()
            err = sys.stderr.getvalue()
            if out:
                self._append_output(out)
            if err:
                self._append_output(err)
        except Exception:
            self._append_output(traceback.format_exc())
        finally:
            sys.stdout = old_stdout
            sys.stderr = old_stderr

        self._append_output(self.PS1)
