package com.pyn.explorer

import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pyn.R
import com.pyn.project.ProjectFile
import com.pyn.project.ProjectManager

data class FileTreeItem(
    val file: ProjectFile,
    val depth: Int,
    val hasChildren: Boolean = false
)

class FileExplorerAdapter(
    private val onFileClick: (ProjectFile) -> Unit,
    private val onFolderClick: (ProjectFile) -> Unit,
    private val onFileLongClick: (ProjectFile, View) -> Unit
) : RecyclerView.Adapter<FileExplorerAdapter.ViewHolder>() {

    private var items: List<FileTreeItem> = emptyList()
    private val expandedPaths = mutableSetOf<String>()

    fun rebuild(root: ProjectFile) {
        applyExpandedState(root)
        items = flattenTree(root, 0)
        notifyDataSetChanged()
    }

    private fun applyExpandedState(node: ProjectFile) {
        node.expanded = node.path in expandedPaths || node.path == ProjectManager.getProjectRoot().absolutePath
        for (child in node.children) {
            applyExpandedState(child)
        }
    }

    private fun flattenTree(node: ProjectFile, depth: Int): List<FileTreeItem> {
        val result = mutableListOf<FileTreeItem>()
        result.add(FileTreeItem(file = node, depth = depth, hasChildren = node.children.isNotEmpty()))
        if (node.expanded) {
            for (child in node.children) {
                result.addAll(flattenTree(child, depth + 1))
            }
        }
        return result
    }

    fun toggleFolder(node: ProjectFile) {
        val path = node.path
        if (path in expandedPaths) {
            expandedPaths.remove(path)
            node.expanded = false
        } else {
            expandedPaths.add(path)
            node.expanded = true
        }
        val root = ProjectManager.buildFileTree()
        rebuild(root)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val file = item.file

        holder.name.text = file.name
        holder.name.setTypeface(null, if (file.name == "main.py") Typeface.BOLD else Typeface.NORMAL)

        val density = holder.itemView.resources.displayMetrics.density
        val indent = (16 + item.depth * 20) * density
        holder.itemView.setPadding(indent.toInt(), 4, 8, 4)

        if (file.isDirectory) {
            holder.icon.setImageResource(
                if (file.expanded) R.drawable.ic_folder_open
                else R.drawable.ic_folder
            )
            holder.icon.setColorFilter(Color.parseColor("#c586c0"))
            holder.name.setTextColor(Color.parseColor("#d4d4d4"))
        } else {
            holder.icon.setImageResource(
                if (file.isPython) R.drawable.ic_code
                else R.drawable.ic_file
            )
            holder.icon.setColorFilter(
                if (file.isPython) Color.parseColor("#569cd6")
                else Color.parseColor("#858585")
            )
            holder.name.setTextColor(Color.parseColor("#d4d4d4"))
        }

        holder.itemView.setOnClickListener {
            if (file.isDirectory) {
                toggleFolder(file)
                onFolderClick(file)
            } else {
                onFileClick(file)
            }
        }

        holder.itemView.setOnLongClickListener {
            onFileLongClick(file, holder.itemView)
            true
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.file_icon)
        val name: TextView = itemView.findViewById(R.id.file_name)
    }
}
