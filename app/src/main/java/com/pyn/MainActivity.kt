package com.pyn

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.chaquo.python.Python
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var output: TextView
    private lateinit var outputScroll: ScrollView
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        editor = findViewById(R.id.editor)
        output = findViewById(R.id.output)
        outputScroll = findViewById(R.id.output_scroll)

        initPython()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_run -> { runCode(); true }
            R.id.action_clear -> { output.text = ""; true }
            R.id.action_new -> { editor.setText(""); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initPython() {
        try {
            Python.getInstance()
            output.text = "Python 就绪\n"
        } catch (e: Exception) {
            output.text = "Python 初始化失败: ${e.message}\n"
        }
    }

    private fun runCode() {
        val code = editor.text.toString().trim()
        if (code.isEmpty()) {
            output.text = "请输入代码\n"
            return
        }

        output.text = "▶ 运行中...\n"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                executePython(code)
            }
            output.text = result
            outputScroll.post { outputScroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun executePython(code: String): String {
        return try {
            val py = Python.getInstance()
            val runner = py.getModule("pyandroid.runner")
            val result = runner.callAttr("execute", code)
            result.toString()
        } catch (e: Exception) {
            "Python 执行错误: ${e.message}"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
