package com.example.photouploaderapp.configs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R

class FolderAdapter(private val folders: MutableList<Folder>) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    // --- Интерфейсы для кликов ---
    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    interface OnDeleteClickListener {
        fun onDeleteClick(position: Int)
    }

    interface OnResetCacheClickListener {
        fun onResetCacheClick(position: Int)
    }

    // --- Слушатели ---
    private var itemClickListener: OnItemClickListener? = null
    private var deleteClickListener: OnDeleteClickListener? = null
    private var resetCacheClickListener: OnResetCacheClickListener? = null
    private lateinit var recyclerView: RecyclerView

    // --- Сеттеры для слушателей ---
    fun setOnItemClickListener(listener: OnItemClickListener) { this.itemClickListener = listener }
    fun setOnDeleteClickListener(listener: OnDeleteClickListener) { this.deleteClickListener = listener }
    fun setOnResetCacheClickListener(listener: OnResetCacheClickListener) { this.resetCacheClickListener = listener }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.folder_item, parent, false)
        return FolderViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val currentFolder = folders[position]
        holder.folderName.text = currentFolder.name
        val folderStatus = if (currentFolder.path.isNotEmpty()) holder.itemView.context.getString(R.string.selected) else holder.itemView.context.getString(R.string.not_selected)
        holder.folderDetails.text = holder.itemView.context.getString(R.string.folder_details, currentFolder.topic.ifEmpty { "-" }, currentFolder.mediaType, folderStatus)
        holder.ivFolderIcon.setImageResource(R.drawable.ic_folder)

        // Обработчики кликов
        holder.itemView.setOnClickListener {
            itemClickListener?.onItemClick(position)
        }

        holder.deleteButton.setOnClickListener {
            deleteClickListener?.onDeleteClick(holder.adapterPosition)
        }

        holder.resetCacheButton.setOnClickListener {
            resetCacheClickListener?.onResetCacheClick(holder.adapterPosition)
        }
    }

    override fun getItemCount() = folders.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    // --- ViewHolder ---
    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderName: TextView = itemView.findViewById(R.id.tvFolderName)
        val folderDetails: TextView = itemView.findViewById(R.id.tvFolderDetails)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteFolder)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val ivFolderIcon: ImageView = itemView.findViewById(R.id.ivFolderIcon)
        val resetCacheButton: ImageButton = itemView.findViewById(R.id.btnResetCache)
    }
}
