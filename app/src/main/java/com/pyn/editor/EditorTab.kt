package com.pyn.editor

data class EditorTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    val path: String,
    val title: String,
    var content: String = "",
    var savedContent: String = "",
    var isModified: Boolean = false,
    var scrollPosition: Int = 0
) {
    val isNewFile: Boolean get() = path.isEmpty()
    val displayTitle: String get() = if (isModified) "$title ●" else title
}
