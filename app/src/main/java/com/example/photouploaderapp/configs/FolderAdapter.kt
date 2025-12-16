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

    interface OnSyncToggleListener {
        fun onSyncToggle(position: Int, isChecked: Boolean)
    }

    private var itemClickListener: OnItemClickListener? = null
    private var deleteClickListener: OnDeleteClickListener? = null
    private var resetCacheClickListener: OnResetCacheClickListener? = null
    private var syncToggleListener: OnSyncToggleListener? = null

    fun setOnItemClickListener(listener: OnItemClickListener) { this.itemClickListener = listener }
    fun setOnDeleteClickListener(listener: OnDeleteClickListener) { this.deleteClickListener = listener }
    fun setOnResetCacheClickListener(listener: OnResetCacheClickListener) { this.resetCacheClickListener = listener }
    fun setOnSyncToggleListener(listener: OnSyncToggleListener) { this.syncToggleListener = listener }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = FolderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val currentFolder = folders[position]
        holder.bind(currentFolder)
    }

    override fun getItemCount() = folders.size

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
            binding.tvFolderDetails.text = context.getString(R.string.folder_details, folder.topic.ifEmpty { "-" }, folder.mediaType)
            binding.ivFolderIcon.setImageResource(R.drawable.ic_folder)
            binding.cbSyncToggle.setOnCheckedChangeListener(null)

            binding.cbSyncToggle.isChecked = folder.isSyncing

            binding.cbSyncToggle.setOnCheckedChangeListener { _, isChecked ->
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    syncToggleListener?.onSyncToggle(adapterPosition, isChecked)
                }
            }
        }
    }
}
