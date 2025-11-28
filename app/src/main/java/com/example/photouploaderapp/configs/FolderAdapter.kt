package com.example.photouploaderapp.configs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.FolderItemBinding

class FolderAdapter(private val folders: MutableList<Folder>) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    interface OnDeleteClickListener {
        fun onDeleteClick(position: Int)
    }

    interface OnResetCacheClickListener {
        fun onResetCacheClick(position: Int)
    }

    private var itemClickListener: OnItemClickListener? = null
    private var deleteClickListener: OnDeleteClickListener? = null
    private var resetCacheClickListener: OnResetCacheClickListener? = null
    private lateinit var recyclerView: RecyclerView

    fun setOnItemClickListener(listener: OnItemClickListener) { this.itemClickListener = listener }
    fun setOnDeleteClickListener(listener: OnDeleteClickListener) { this.deleteClickListener = listener }
    fun setOnResetCacheClickListener(listener: OnResetCacheClickListener) { this.resetCacheClickListener = listener }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = FolderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val currentFolder = folders[position]
        holder.bind(currentFolder)
    }

    override fun getItemCount() = folders.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }
    inner class FolderViewHolder(private val binding: FolderItemBinding) : RecyclerView.ViewHolder(binding.root) {

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    itemClickListener?.onItemClick(adapterPosition)
                }
            }

            binding.btnDeleteFolder.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    deleteClickListener?.onDeleteClick(adapterPosition)
                }
            }

            binding.btnResetCache.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    resetCacheClickListener?.onResetCacheClick(adapterPosition)
                }
            }
        }

        fun bind(folder: Folder) {
            val context = itemView.context
            binding.tvFolderName.text = folder.name
            val folderStatus = if (folder.path.isNotEmpty()) context.getString(R.string.selected) else context.getString(R.string.not_selected)
            binding.tvFolderDetails.text = context.getString(R.string.folder_details, folder.topic.ifEmpty { "-" }, folder.mediaType, folderStatus)
            binding.ivFolderIcon.setImageResource(R.drawable.ic_folder)
        }
    }
}
