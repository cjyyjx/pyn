package com.pyn

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.ScrollingMovementMethod
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.pyn.editor.EditorTab
import com.pyn.editor.ObservableEditText
import com.pyn.editor.PythonSyntaxHighlighter
import com.pyn.explorer.FileExplorerAdapter
import com.pyn.project.ProjectFile
import com.pyn.project.ProjectManager
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var tabStrip: LinearLayout
    private lateinit var editor: ObservableEditText
    private lateinit var lineNumbers: TextView
    private lateinit var outputText: TextView
    private lateinit var outputPanel: View
    private lateinit var outputTabStdout: View
    private lateinit var outputTabStderr: View
    private lateinit var drawerFileList: RecyclerView
    private lateinit var statusText: TextView

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var highlightJob: Job? = null
    private var lastHighlightText = ""
    private var isHighlighting = false

    private val openTabs = mutableListOf<EditorTab>()
    private var activeTabIndex = -1
    private var outputMode = 0 // 0 = stdout, 1 = stderr, 2 = both

    private var fileExplorerAdapter: FileExplorerAdapter? = null

    private var stdOut = ""
    private var stdErr = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            val fos = openFileOutput("crash.log", MODE_PRIVATE)
            fos.write(("CRASH: ${e.javaClass.name}: ${e.message}\n${e.stackTraceToString()}\n").toByteArray())
            fos.close()
            throw e
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        toolbar = findViewById(R.id.toolbar)
        tabStrip = findViewById(R.id.tab_strip)
        editor = findViewById(R.id.code_editor)
        lineNumbers = findViewById(R.id.line_numbers)
        outputText = findViewById(R.id.output_text)
        outputPanel = findViewById(R.id.output_panel)
        outputTabStdout = findViewById(R.id.output_tab_stdout)
        outputTabStderr = findViewById(R.id.output_tab_stderr)
        drawerFileList = findViewById(R.id.drawer_file_list)
        statusText = findViewById(R.id.status_text)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationIcon(R.drawable.ic_menu)
        toolbar.setNavigationContentDescription("打开文件浏览器")

        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        outputText.movementMethod = ScrollingMovementMethod()
        lineNumbers.movementMethod = ScrollingMovementMethod()

        outputTabStdout.setOnClickListener { switchOutputTab(0) }
        outputTabStderr.setOnClickListener { switchOutputTab(1) }

        findViewById<View>(R.id.btn_new_file).setOnClickListener { showNewFileDialog() }
        findViewById<View>(R.id.btn_refresh).setOnClickListener { refreshFileTree() }
        findViewById<View>(R.id.btn_clear_output).setOnClickListener { clearOutput() }

        setupEditor()
        setupFileExplorer()
        initPython()
        createNewTab("", "untitled.py")
    }

    private fun setupEditor() {
        editor.onScrollChangedListener = { _, vert, _, _ ->
            lineNumbers.scrollTo(0, vert)
        }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLineNumbers()
                markTabModified()
                scheduleHighlight()
            }
        })

        editor.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) updateStatus("就绪")
        }
    }

    private fun updateLineNumbers() {
        val count = editor.lineCount
        val sb = StringBuilder()
        for (i in 1..count) sb.appendLine(i.toString())
        lineNumbers.text = sb.toString()

        val params = lineNumbers.layoutParams
        val digits = count.toString().length
        params.width = (digits * 12 + 24) * resources.displayMetrics.density.toInt()
        lineNumbers.layoutParams = params
    }

    private fun scheduleHighlight() {
        highlightJob?.cancel()
        val text = editor.text.toString()
        if (text == lastHighlightText) return
        highlightJob = scope.launch {
            delay(200)
            if (!isActive) return@launch
            val currentText = editor.text.toString()
            if (currentText.isEmpty()) return@launch
            isHighlighting = true
            try {
                val tokens = withContext(Dispatchers.Default) {
                    PythonSyntaxHighlighter.highlight(currentText)
                }
                val editable = editor.text ?: return@launch
                for (span in editable.getSpans(0, editable.length, android.text.style.ForegroundColorSpan::class.java)) {
                    editable.removeSpan(span)
                }
                for (token in tokens) {
                    if (token.start < editable.length && token.end <= editable.length) {
                        editable.setSpan(
                            android.text.style.ForegroundColorSpan(token.color),
                            token.start, token.end,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
                lastHighlightText = currentText
            } finally {
                isHighlighting = false
            }
        }
    }

    private fun updateLineNumbersImmediate() {
        val count = editor.lineCount
        val sb = StringBuilder()
        for (i in 1..count) sb.appendLine(i.toString())
        lineNumbers.text = sb.toString()
    }

    private fun setupFileExplorer() {
        drawerFileList.layoutManager = LinearLayoutManager(this)

        fileExplorerAdapter = FileExplorerAdapter(
            onFileClick = { file -> openFile(file.path) },
            onFolderClick = { file ->
            },
            onFileLongClick = { file, view -> showFileContextMenu(file, view) }
        )
        drawerFileList.adapter = fileExplorerAdapter

        refreshFileTree()
    }

    private fun refreshFileTree() {
        val root = ProjectManager.buildFileTree()
        fileExplorerAdapter?.rebuild(root)
    }

    private fun openFile(path: String) {
        val existing = openTabs.indexOfFirst { it.path == path }
        if (existing >= 0) {
            switchToTab(existing)
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }

        val content = ProjectManager.readFile(path)
        val name = path.substringAfterLast('/')
        val tab = EditorTab(path = path, title = name, content = content, savedContent = content)
        openTabs.add(tab)
        switchToTab(openTabs.size - 1)
        drawerLayout.closeDrawer(GravityCompat.START)
        updateStatus("已打开 $name")
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= openTabs.size) return

        if (activeTabIndex >= 0 && activeTabIndex < openTabs.size) {
            openTabs[activeTabIndex] = openTabs[activeTabIndex].copy(
                content = editor.text.toString(),
                scrollPosition = editor.scrollY
            )
        }

        activeTabIndex = index
        val tab = openTabs[index]

        editor.setText(tab.content)
        editor.scrollTo(0, tab.scrollPosition)
        updateLineNumbers()
        lastHighlightText = ""
        scheduleHighlight()

        supportActionBar?.title = tab.title
        toolbar.subtitle = if (tab.isModified) "已修改" else ""

        renderTabStrip()
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun closeTab(index: Int) {
        if (openTabs.size <= 1) return
        val tab = openTabs[index]
        if (tab.isModified) {
            AlertDialog.Builder(this)
                .setTitle("保存文件")
                .setMessage("'${tab.title}' 已修改，是否保存？")
                .setPositiveButton("保存") { _, _ ->
                    saveTabContent(index)
                    doCloseTab(index)
                }
                .setNegativeButton("不保存") { _, _ -> doCloseTab(index) }
                .setNeutralButton("取消", null)
                .show()
        } else {
            doCloseTab(index)
        }
    }

    private fun doCloseTab(index: Int) {
        if (openTabs.size <= 1) return
        openTabs.removeAt(index)
        val newIndex = when {
            index < openTabs.size -> index
            else -> openTabs.size - 1
        }
        switchToTab(newIndex)
    }

    private fun renderTabStrip() {
        tabStrip.removeAllViews()
        for ((i, tab) in openTabs.withIndex()) {
            val tabView = layoutInflater.inflate(R.layout.item_tab, tabStrip, false)
            val titleTv = tabView.findViewById<TextView>(R.id.tab_title)
            val closeBtn = tabView.findViewById<ImageView>(R.id.tab_close)

            titleTv.text = tab.displayTitle
            titleTv.isSelected = (i == activeTabIndex)
            tabView.isSelected = (i == activeTabIndex)

            val finalI = i
            tabView.setOnClickListener { switchToTab(finalI) }
            closeBtn.setOnClickListener { closeTab(finalI) }

            tabStrip.addView(tabView)
        }

        if (activeTabIndex >= 0 && activeTabIndex < openTabs.size) {
            tabStrip.getChildAt(activeTabIndex)?.let {
                it.post { it.requestFocus() }
            }
        }
    }

    private fun markTabModified() {
        if (activeTabIndex < 0 || activeTabIndex >= openTabs.size) return
        val current = openTabs[activeTabIndex]
        val newContent = editor.text.toString()
        if (current.content != newContent) {
            openTabs[activeTabIndex] = current.copy(
                content = newContent,
                isModified = newContent != current.savedContent
            )
            supportActionBar?.title = openTabs[activeTabIndex].title
            toolbar.subtitle = if (openTabs[activeTabIndex].isModified) "已修改" else ""
            renderTabStrip()
        }
    }

    private fun saveCurrentTab() {
        if (activeTabIndex < 0) return
        val tab = openTabs[activeTabIndex]
        val content = editor.text.toString()

        if (tab.path.isEmpty() || !ProjectManager.isChildOfProject(tab.path)) {
            showSaveAsDialog()
            return
        }

        ProjectManager.writeFile(tab.path, content)
        openTabs[activeTabIndex] = tab.copy(content = content, savedContent = content, isModified = false)
        toolbar.subtitle = ""
        renderTabStrip()
        updateStatus("已保存 ${tab.title}")
        refreshFileTree()
    }

    private fun saveTabContent(index: Int) {
        if (index < 0 || index >= openTabs.size) return
        val tab = openTabs[index]
        if (tab.path.isEmpty() || !ProjectManager.isChildOfProject(tab.path)) return
        val content = if (index == activeTabIndex) editor.text.toString() else tab.content
        ProjectManager.writeFile(tab.path, content)
        openTabs[index] = tab.copy(content = content, savedContent = content, isModified = false)
        if (index == activeTabIndex) {
            toolbar.subtitle = ""
        }
        renderTabStrip()
    }

    private fun showSaveAsDialog() {
        val input = EditText(this).apply {
            setText("untitled.py")
            setSelection(0, 10)
            hint = "文件名"
        }
        AlertDialog.Builder(this)
            .setTitle("另存为")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val path = ProjectManager.resolvePath(name)
                    ProjectManager.createFile(path)
                    val content = editor.text.toString()
                    ProjectManager.writeFile(path, content)
                    if (activeTabIndex >= 0) {
                        openTabs[activeTabIndex] = openTabs[activeTabIndex].copy(
                            path = path, title = name,
                            savedContent = content, isModified = false
                        )
                        renderTabStrip()
                    }
                    refreshFileTree()
                    updateStatus("已保存 $name")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNewFileDialog() {
        val input = EditText(this).apply {
            setText("new_file.py")
            setSelection(0, 11)
            hint = "文件名"
        }
        AlertDialog.Builder(this)
            .setTitle("新建文件")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val path = ProjectManager.resolvePath(name)
                    if (ProjectManager.createFile(path)) {
                        openFile(path)
                        updateStatus("已创建 $name")
                    } else {
                        updateStatus("文件已存在: $name")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showNewFolderDialog() {
        val input = EditText(this).apply {
            setText("new_folder")
            setSelection(0, 10)
            hint = "文件夹名"
        }
        AlertDialog.Builder(this)
            .setTitle("新建文件夹")
            .setView(input)
            .setPositiveButton("创建") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val path = ProjectManager.resolvePath(name)
                    if (ProjectManager.createDirectory(path)) {
                        refreshFileTree()
                        updateStatus("已创建文件夹 $name")
                    } else {
                        updateStatus("创建失败")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showFileContextMenu(file: ProjectFile, anchor: View) {
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (file.isDirectory) {
            items.add("新建文件")
            actions.add {
                val input = EditText(this).apply {
                    hint = "文件名"
                    setText("new.py")
                    setSelection(0, 6)
                }
                AlertDialog.Builder(this)
                    .setTitle("在 ${file.name} 中新建文件")
                    .setView(input)
                    .setPositiveButton("创建") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotEmpty()) {
                            val path = file.path + "/" + name
                            if (ProjectManager.createFile(path)) {
                                openFile(path)
                                refreshFileTree()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            items.add("新建文件夹")
            actions.add {
                val input = EditText(this).apply { hint = "文件夹名" }
                AlertDialog.Builder(this)
                    .setTitle("在 ${file.name} 中新建文件夹")
                    .setView(input)
                    .setPositiveButton("创建") { _, _ ->
                        val name = input.text.toString().trim()
                        if (name.isNotEmpty()) {
                            ProjectManager.createDirectory(file.path + "/" + name)
                            refreshFileTree()
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            items.add("删除文件夹")
            actions.add {
                confirmDelete(file) { ProjectManager.deleteFile(file.path); refreshFileTree() }
            }
        } else {
            items.add("重命名")
            actions.add {
                val input = EditText(this).apply {
                    setText(file.name)
                    setSelection(0, file.name.length - file.extension.length - 1)
                }
                AlertDialog.Builder(this)
                    .setTitle("重命名")
                    .setView(input)
                    .setPositiveButton("确定") { _, _ ->
                        val newName = input.text.toString().trim()
                        if (newName.isNotEmpty()) {
                            val newPath = file.path.substringBeforeLast('/') + "/" + newName
                            ProjectManager.renameFile(file.path, newPath)
                            refreshFileTree()
                            val tabIdx = openTabs.indexOfFirst { it.path == file.path }
                            if (tabIdx >= 0) {
                                openTabs[tabIdx] = openTabs[tabIdx].copy(path = newPath, title = newName)
                                renderTabStrip()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            items.add("删除文件")
            actions.add {
                confirmDelete(file) {
                    ProjectManager.deleteFile(file.path)
                    val tabIdx = openTabs.indexOfFirst { it.path == file.path }
                    if (tabIdx >= 0) doCloseTab(tabIdx)
                    refreshFileTree()
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(items.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun confirmDelete(file: ProjectFile, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定删除 '${file.name}' 吗？")
            .setPositiveButton("删除") { _, _ -> onConfirm() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun switchOutputTab(mode: Int) {
        outputMode = mode
        val selectedBg = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#3e3e3e"))
        val defaultBg = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#2d2d2d"))
        outputTabStdout.background = if (mode == 0 || mode == 2) selectedBg else defaultBg
        outputTabStderr.background = if (mode == 1 || mode == 2) selectedBg else defaultBg
        updateOutputDisplay()
    }

    private fun updateOutputDisplay() {
        when (outputMode) {
            0 -> outputText.text = if (stdOut.isNotEmpty()) stdOut else "(无输出)"
            1 -> outputText.text = if (stdErr.isNotEmpty()) stdErr else "(无错误)"
            2 -> outputText.text = (if (stdOut.isNotEmpty()) "=== stdout ===\n$stdOut\n" else "") +
                    (if (stdErr.isNotEmpty()) "=== stderr ===\n$stdErr" else "")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_run -> { runCurrentCode(); true }
            R.id.action_run_file -> { runCurrentFile(); true }
            R.id.action_save -> { saveCurrentTab(); true }
            R.id.action_new -> { showNewFileDialog(); true }
            R.id.action_new_folder -> { showNewFolderDialog(); true }
            R.id.action_packages -> { showPackageManager(); true }
            R.id.action_format -> { formatCode(); true }
            R.id.action_toggle_output -> { toggleOutputPanel(); true }
            R.id.action_clear_output -> { clearOutput(); true }
            R.id.action_reset -> { resetPythonNamespace(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ---------- Python ----------

    private fun initPython() {
        try {
            ProjectManager.init(applicationContext)
            Python.getInstance()
            appendOutput("Python 3.10 就绪 | ${ProjectManager.getProjectName()}\n")
            updateStatus("就绪")
        } catch (e: Exception) {
            appendOutput("Python 初始化失败: ${e.message}\n")
            updateStatus("错误: Python 不可用")
        }
    }

    private fun runCurrentCode() {
        val code = editor.text.toString().trim()
        if (code.isEmpty()) {
            appendOutput("请输入代码\n")
            return
        }
        runPython(code)
    }

    private fun runCurrentFile() {
        if (activeTabIndex < 0) return
        val tab = openTabs[activeTabIndex]
        if (tab.path.isNotEmpty() && ProjectManager.isChildOfProject(tab.path)) {
            saveCurrentTab()
            appendOutput("▶ 运行文件: ${tab.title}\n")
            runPythonViaFile(tab.path)
        } else {
            runCurrentCode()
        }
    }

    private fun runPython(code: String) {
        showOutputPanel()
        appendOutput("▶ 运行...\n")
        updateStatus("运行中...")

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val runner = py.getModule("pyandroid.runner")
                    runner.callAttr("execute", code).toString()
                } catch (e: Exception) {
                    "Python 错误: ${e.message}"
                }
            }
            processResult(result)
        }
    }

    private fun runPythonViaFile(path: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val runner = py.getModule("pyandroid.runner")
                    runner.callAttr("execute_file", path).toString()
                } catch (e: Exception) {
                    "Python 错误: ${e.message}"
                }
            }
            processResult(result)
        }
    }

    private fun processResult(result: String) {
        val lines = result.split("\n")
        val outLines = mutableListOf<String>()
        val errLines = mutableListOf<String>()

        for (line in lines) {
            if (line.contains("Traceback") || line.contains("Error:") ||
                line.startsWith("  File ") || line.startsWith("SyntaxError") ||
                line.startsWith("NameError") || line.startsWith("TypeError") ||
                line.startsWith("ValueError") || line.startsWith("KeyError") ||
                line.startsWith("IndexError") || line.startsWith("AttributeError") ||
                line.startsWith("ImportError") || line.startsWith("ModuleNotFoundError") ||
                line.startsWith("ZeroDivisionError") || line.startsWith("IndentationError")
            ) {
                errLines.add(line)
            } else {
                outLines.add(line)
            }
        }

        stdOut = outLines.joinToString("\n")
        stdErr = errLines.joinToString("\n")

        if (errLines.isNotEmpty()) {
            appendOutput(stdOut + "\n" + stdErr + "\n")
            switchOutputTab(1)
        } else {
            appendOutput(result + "\n")
            switchOutputTab(0)
        }

        updateStatus("完成 (${result.length} 字符)")
    }

    private fun resetPythonNamespace() {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val runner = py.getModule("pyandroid.runner")
                    runner.callAttr("reset_namespace").toString()
                } catch (e: Exception) {
                    "错误: ${e.message}"
                }
            }
            appendOutput("--- $result ---\n")
            updateStatus("命名空间已重置")
        }
    }

    // ---------- Package Manager ----------

    private fun showPackageManager() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val pkgInput = EditText(this).apply {
            hint = "包名 (如: requests)"
        }

        val installBtn = Button(this).apply { setText("安装") }
        val uninstallBtn = Button(this).apply { setText("卸载") }
        val listBtn = Button(this).apply { setText("列出已安装") }
        val resultTv = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(android.graphics.Color.parseColor("#d4d4d4"))
            minLines = 3
        }

        layout.addView(pkgInput)
        layout.addView(installBtn)
        layout.addView(uninstallBtn)
        layout.addView(listBtn)
        layout.addView(resultTv)

        val dialog = AlertDialog.Builder(this)
            .setTitle("包管理器 (pip)")
            .setView(layout)
            .setPositiveButton("关闭", null)
            .show()

        installBtn.setOnClickListener {
            val pkg = pkgInput.text.toString().trim()
            if (pkg.isNotEmpty()) {
                resultTv.text = "正在安装 $pkg..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val py = Python.getInstance()
                            val pm = py.getModule("pyandroid.pkg_manager")
                            pm.callAttr("install_package", pkg).toString()
                        } catch (e: Exception) { "错误: ${e.message}" }
                    }
                    resultTv.text = result
                }
            }
        }

        uninstallBtn.setOnClickListener {
            val pkg = pkgInput.text.toString().trim()
            if (pkg.isNotEmpty()) {
                resultTv.text = "正在卸载 $pkg..."
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        try {
                            val py = Python.getInstance()
                            val pm = py.getModule("pyandroid.pkg_manager")
                            pm.callAttr("uninstall_package", pkg).toString()
                        } catch (e: Exception) { "错误: ${e.message}" }
                    }
                    resultTv.text = result
                }
            }
        }

        listBtn.setOnClickListener {
            resultTv.text = "正在查询..."
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance()
                        val pm = py.getModule("pyandroid.pkg_manager")
                        pm.callAttr("list_installed").toString()
                    } catch (e: Exception) { "错误: ${e.message}" }
                }
                resultTv.text = result
            }
        }
    }

    // ---------- Format ----------

    private fun formatCode() {
        val code = editor.text.toString()
        if (code.isEmpty()) return
        updateStatus("格式化中...")
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val intelligence = py.getModule("pyandroid.intellisense")
                    intelligence.callAttr("format_code", code).toString()
                } catch (e: Exception) { code }
            }
            if (result != code) {
                editor.setText(result)
                updateStatus("已格式化")
            } else {
                updateStatus("无需格式化")
            }
        }
    }

    // ---------- UI Helpers ----------

    private fun appendOutput(text: String) {
        outputText.append(text)
        val scrollView = outputText.parent as? ScrollView
        scrollView?.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun clearOutput() {
        stdOut = ""
        stdErr = ""
        outputText.text = ""
        switchOutputTab(0)
    }

    private fun toggleOutputPanel() {
        outputPanel.visibility = if (outputPanel.visibility == View.GONE) View.VISIBLE else View.GONE
    }

    private fun showOutputPanel() {
        if (outputPanel.visibility == View.GONE) {
            outputPanel.visibility = View.VISIBLE
        }
    }

    private fun updateStatus(msg: String) {
        statusText.text = msg
    }

    private fun createNewTab(path: String, name: String) {
        val tab = EditorTab(
            path = path,
            title = name,
            content = if (path.isNotEmpty()) ProjectManager.readFile(path) else
                "# PyAndroid - Python IDE for Android\n# 开始编写你的 Python 代码\n\nprint(\"Hello, PyAndroid!\")\n",
            savedContent = ""
        )
        openTabs.add(tab)
        switchToTab(openTabs.size - 1)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}
