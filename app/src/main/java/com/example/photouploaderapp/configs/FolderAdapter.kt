package com.example.photouploaderapp.configs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R
import android.widget.ImageView
import com.google.gson.Gson
import com.google.common.reflect.TypeToken

class FolderAdapter(private val folders: MutableList<Folder>) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {

    private val gson = Gson()

    interface OnItemClickListener {
        fun onItemClick(position: Int)
    }

    interface OnDeleteClickListener {
        fun onDeleteClick(position: Int)
    }

    interface OnCancelClickListener {
        fun onCancelClick(position: Int)
    }

    private var itemClickListener: OnItemClickListener? = null
    private var deleteClickListener: OnDeleteClickListener? = null
    private var cancelClickListener: OnCancelClickListener? = null
    private lateinit var recyclerView: RecyclerView

    fun setOnItemClickListener(listener: OnItemClickListener) {
        this.itemClickListener = listener
    }

    fun setOnDeleteClickListener(listener: OnDeleteClickListener) {
        this.deleteClickListener = listener
    }

    fun setOnCancelClickListener(listener: OnCancelClickListener) {
        this.cancelClickListener = listener
    }

    fun updateProgress(position: Int, progress: Int) {
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? FolderViewHolder
        holder?.progressBar?.progress = progress
        holder?.folderDetails?.text = holder?.itemView?.context?.getString(R.string.loading_progress, progress)
    }

    fun showProgress(position: Int) {
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? FolderViewHolder
        holder?.progressBar?.visibility = View.VISIBLE
    }

    fun hideProgress(position: Int) {
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? FolderViewHolder
        holder?.progressBar?.visibility = View.GONE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.folder_item, parent, false)
        return FolderViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val currentFolder = folders[position]
        holder.folderName.text = currentFolder.name
        val folderStatus = if (currentFolder.path.isNotEmpty()) holder.itemView.context.getString(R.string.selected) else holder.itemView.context.getString(R.string.not_selected)
        holder.folderDetails.text = holder.itemView.context.getString(R.string.folder_details, currentFolder.topic, currentFolder.mediaType, folderStatus)
        holder.ivFolderIcon.setImageResource(R.drawable.ic_folder)

        holder.itemView.setOnClickListener {
            itemClickListener?.onItemClick(position)
        }

        holder.deleteButton.setOnClickListener {
            deleteClickListener?.onDeleteClick(holder.adapterPosition)
        }

        holder.cancelButton.setOnClickListener {
            val currentFolder = folders[position]  // Получаем текущую папку
            currentFolder.isSyncing = false  // Изменяем состояние

            val sharedPreferences = holder.itemView.context.getSharedPreferences("Folders", Context.MODE_PRIVATE)
            val foldersJson = sharedPreferences.getString("folders", null)
            if (foldersJson != null) {
                val type = object : TypeToken<MutableList<Folder>>() {}.type
                val folders: MutableList<Folder> = gson.fromJson(foldersJson, type)

                val index = folders.indexOfFirst { it.name == currentFolder.name }
                if (index != -1) {
                    folders[index].isSyncing = false  // Обновляем состояние
                    sharedPreferences.edit().putString("folders", gson.toJson(folders)).apply()
                    notifyItemChanged(index)  // Обновление UI
                }
            }
        }

        if (currentFolder.isSyncing) {
            holder.cancelButton.visibility = View.VISIBLE
        } else {
            holder.cancelButton.visibility = View.GONE
        }
    }

    override fun getItemCount() = folders.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val folderName: TextView = itemView.findViewById(R.id.tvFolderName)
        val folderDetails: TextView = itemView.findViewById(R.id.tvFolderDetails)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btnDeleteFolder)
        val cancelButton: ImageButton = itemView.findViewById(R.id.btnCancelSync)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        val ivFolderIcon: ImageView = itemView.findViewById(R.id.ivFolderIcon)
    }
}