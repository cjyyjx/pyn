package com.pyn.project

import android.content.Context
import java.io.File

data class ProjectFile(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val children: MutableList<ProjectFile> = mutableListOf(),
    var expanded: Boolean = false
) {
    val extension: String get() = if (isDirectory) "" else name.substringAfterLast('.', "")
    val isPython: Boolean get() = extension == "py"
}

object ProjectManager {
    private var projectRoot: File? = null
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx
        projectRoot = File(ctx.filesDir, "project")
        if (!projectRoot!!.exists()) {
            projectRoot!!.mkdirs()
            File(projectRoot, "main.py").writeText(
                "# PyAndroid Project\n# Write your Python code here\n\nprint(\"Hello from PyAndroid!\")\n"
            )
        }
    }

    fun getProjectRoot(): File = projectRoot ?: throw IllegalStateException("ProjectManager not initialized")

    fun getProjectName(): String = "PyAndroidProject"

    fun readFile(path: String): String {
        val file = File(path)
        return if (file.exists()) file.readText() else ""
    }

    fun writeFile(path: String, content: String) {
        File(path).writeText(content)
    }

    fun createFile(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) false else file.createNewFile()
    }

    fun createDirectory(path: String): Boolean {
        return File(path).mkdirs()
    }

    fun deleteFile(path: String): Boolean {
        val file = File(path)
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun renameFile(oldPath: String, newPath: String): Boolean {
        return File(oldPath).renameTo(File(newPath))
    }

    fun listFiles(dir: File = getProjectRoot()): List<ProjectFile> {
        val files = dir.listFiles() ?: return emptyList()
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).map { file ->
            ProjectFile(
                path = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory
            )
        }
    }

    fun buildFileTree(): ProjectFile {
        val root = getProjectRoot()
        return buildTree(root)
    }

    private fun buildTree(dir: File): ProjectFile {
        val node = ProjectFile(
            path = dir.absolutePath,
            name = dir.name,
            isDirectory = true,
            expanded = true
        )
        val files = dir.listFiles() ?: return node
        for (file in files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))) {
            if (file.isDirectory) {
                node.children.add(buildTree(file))
            } else {
                node.children.add(
                    ProjectFile(
                        path = file.absolutePath,
                        name = file.name,
                        isDirectory = false
                    )
                )
            }
        }
        return node
    }

    fun resolvePath(relativePath: String): String {
        return File(getProjectRoot(), relativePath).absolutePath
    }

    fun isChildOfProject(path: String): Boolean {
        return File(path).absolutePath.startsWith(getProjectRoot().absolutePath)
    }
}
