import os, sys
from kivy.app import App
from kivy.uix.label import Label
from kivy.core.window import Window

class TestApp(App):
    def build(self):
        self.title = "KivyTest"
        Window.clearcolor = (0.12, 0.12, 0.12, 1)
        return Label(text="OK", color=(1,1,1,1))

if __name__ == "__main__":
    TestApp().run()
