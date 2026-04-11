package com.example.photouploaderapp.configs

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.FolderItemBinding
import com.example.photouploaderapp.databinding.PreviewItemBinding
import kotlinx.coroutines.*

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

    interface OnShowAllPreviewsListener {
        fun onShowAllPreviews(folder: Folder, shownIdentifiers: List<String>)
    }

    private var itemClickListener: OnItemClickListener? = null
    private var deleteClickListener: OnDeleteClickListener? = null
    private var resetCacheClickListener: OnResetCacheClickListener? = null
    private var syncToggleListener: OnSyncToggleListener? = null
    private var showAllPreviewsListener: OnShowAllPreviewsListener? = null

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setOnItemClickListener(listener: OnItemClickListener) { this.itemClickListener = listener }
    fun setOnDeleteClickListener(listener: OnDeleteClickListener) { this.deleteClickListener = listener }
    fun setOnResetCacheClickListener(listener: OnResetCacheClickListener) { this.resetCacheClickListener = listener }
    fun setOnSyncToggleListener(listener: OnSyncToggleListener) { this.syncToggleListener = listener }
    fun setOnShowAllPreviewsListener(listener: OnShowAllPreviewsListener) { this.showAllPreviewsListener = listener }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val binding = FolderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        val currentFolder = folders[position]
        holder.bind(currentFolder)
    }

    override fun getItemCount() = folders.size

    fun onDetach() {
        adapterScope.cancel()
    }

    inner class FolderViewHolder(private val binding: FolderItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null
        private var lastPreviewsState: List<Pair<String, Boolean>> = emptyList()
        private var lastTotalCount: Int = -1
        private var lastFolderId: String? = null

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
            
            if (lastFolderId != folder.id) {
                lastFolderId = folder.id
                lastPreviewsState = emptyList()
                lastTotalCount = -1
            }
            
            binding.tvFolderName.text = folder.name
            
            if (folder.hasTopicError) {
                binding.tvFolderName.setTextColor(context.getColor(android.R.color.holo_red_dark))
            } else {
                val typedValue = android.util.TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                binding.tvFolderName.setTextColor(typedValue.data)
            }

            binding.tvFolderDetails.text = context.getString(R.string.folder_details, folder.topic.ifEmpty { "-" }, folder.mediaType)
            binding.ivFolderIcon.setImageResource(R.drawable.ic_folder)
            binding.cbSyncToggle.setOnCheckedChangeListener(null)

            binding.cbSyncToggle.isChecked = folder.isSyncing
            itemView.alpha = if (folder.isSyncing) 1.0f else 0.5f

            binding.cbSyncToggle.setOnCheckedChangeListener { _, isChecked ->
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    if (isChecked) {
                        folder.hasTopicError = false
                    }
                    itemView.alpha = if (isChecked) 1.0f else 0.5f
                    syncToggleListener?.onSyncToggle(adapterPosition, isChecked)
                }
            }

            loadPreviews(folder)
        }

        private fun loadPreviews(folder: Folder) {
            job?.cancel()
            
            job = adapterScope.launch {
                val (newPreviews, totalFound) = withContext(Dispatchers.IO) {
                    getFolderPreviewsFast(itemView.context, folder)
                }
                
                val currentState = newPreviews.map { it.identifier to it.isExcluded }
                
                if (currentState == lastPreviewsState && totalFound == lastTotalCount && lastPreviewsState.isNotEmpty()) {
                    return@launch
                }
                
                lastPreviewsState = currentState
                lastTotalCount = totalFound

                val transition = AutoTransition().apply {
                    duration = 300
                    ordering = AutoTransition.ORDERING_TOGETHER
                }
                TransitionManager.beginDelayedTransition(binding.llPreviews, transition)

                updatePreviewContainer(newPreviews, folder)
            }
        }

        private fun updatePreviewContainer(newPreviews: List<PreviewData>, folder: Folder) {
            val container = binding.llPreviews
            val inflater = LayoutInflater.from(container.context)

            container.removeAllViews()

            if (newPreviews.isEmpty()) {
                addEmptyPreview(container, inflater)
                return
            }

            val shownIdentifiers = newPreviews.filter { !it.isCountOnly }.map { it.identifier }

            newPreviews.forEach { data ->
                val previewBinding = PreviewItemBinding.inflate(inflater, container, false)
                setupPreviewView(previewBinding, data)
                previewBinding.root.tag = data.identifier
                
                previewBinding.root.setOnClickListener {
                    if (data.isCountOnly) {
                        showAllPreviewsListener?.onShowAllPreviews(folder, shownIdentifiers)
                    } else {
                        toggleFileExclusion(container.context, data.identifier, folder)
                    }
                }
                
                container.addView(previewBinding.root)
            }
        }

        private fun toggleFileExclusion(context: Context, identifier: String, folder: Folder) {
            val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
            val isExcluded = excludedFilesPrefs.contains(identifier)
            if (isExcluded) {
                excludedFilesPrefs.edit().remove(identifier).apply()
            } else {
                excludedFilesPrefs.edit().putBoolean(identifier, true).apply()
            }
            loadPreviews(folder)
        }

        private fun setupPreviewView(previewBinding: PreviewItemBinding, data: PreviewData) {
            if (data.isCountOnly) {
                previewBinding.tvMoreCount.visibility = View.VISIBLE
                previewBinding.tvMoreCount.text = if (data.identifier == "audio_count") "${data.count}" else "+${data.count}"
                previewBinding.ivPreview.setImageBitmap(null)
                previewBinding.ivPreview.setBackgroundColor(0xFF444444.toInt())
            } else {
                previewBinding.tvMoreCount.visibility = View.GONE
                if (data.bitmap != null) {
                    previewBinding.ivPreview.setImageBitmap(data.bitmap)
                    previewBinding.ivPreview.scaleType = ImageView.ScaleType.CENTER_CROP
                    previewBinding.ivPreview.clearColorFilter()
                } else {
                    previewBinding.ivPreview.setImageResource(R.drawable.ic_folder)
                    previewBinding.ivPreview.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    previewBinding.ivPreview.setColorFilter(0xFFCCCCCC.toInt())
                    previewBinding.ivPreview.alpha = 0.8f
                }

                if (data.isExcluded) {
                    previewBinding.ivPreview.setColorFilter(0xAA000000.toInt(), android.graphics.PorterDuff.Mode.SRC_ATOP)
                } else {
                    if (data.bitmap != null) {
                        previewBinding.ivPreview.clearColorFilter()
                    }
                }
            }
        }

        private fun addEmptyPreview(container: ViewGroup, inflater: LayoutInflater) {
            val previewBinding = PreviewItemBinding.inflate(inflater, container, false)
            previewBinding.ivPreview.setImageResource(R.drawable.ic_folder)
            previewBinding.ivPreview.scaleType = ImageView.ScaleType.CENTER_INSIDE
            previewBinding.ivPreview.setPadding(12, 12, 12, 12)
            previewBinding.ivPreview.setColorFilter(0xFFCCCCCC.toInt())
            previewBinding.ivPreview.alpha = 0.4f
            previewBinding.root.tag = "empty_placeholder"
            container.addView(previewBinding.root)
        }

        private fun getFolderPreviewsFast(context: Context, folder: Folder): Pair<List<PreviewData>, Int> {
            val result = mutableListOf<PreviewData>()
            val treeUri = Uri.parse(folder.path)
            
            val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
            val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
            val validFiles = mutableListOf<FileInfo>()
            val excludedFiles = mutableListOf<FileInfo>()

            try {
                val documentId = DocumentsContract.getTreeDocumentId(treeUri)
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
                
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )

                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: continue
                        if (name.startsWith(".")) continue
                        
                        val mime = cursor.getString(mimeIdx) ?: ""
                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue

                        val size = cursor.getLong(sizeIdx)
                        val lastModified = cursor.getLong(modIdx)
                        val docId = cursor.getString(idIdx)
                        
                        val fileIdentifier = "folder_${folder.id}_${name}_$size"
                        
                        if (!sentFilesPrefs.contains(fileIdentifier) && MediaUtils.isValidMedia(context, name, folder.mediaType)) {
                            val isExcluded = excludedFilesPrefs.contains(fileIdentifier)
                            if (!isExcluded) {
                                validFiles.add(FileInfo(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                                    name = name,
                                    lastModified = lastModified,
                                    identifier = fileIdentifier,
                                    isVideo = mime.startsWith("video/"),
                                    isExcluded = false
                                ))
                            } else {
                                excludedFiles.add(FileInfo(
                                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                                    name = name,
                                    lastModified = lastModified,
                                    identifier = fileIdentifier,
                                    isVideo = mime.startsWith("video/"),
                                    isExcluded = true
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FolderAdapter", "Fast scan error", e)
                return Pair(emptyList(), 0)
            }

            val totalCount = validFiles.size
            
            val combinedFiles = mutableListOf<FileInfo>()
            combinedFiles.addAll(validFiles.sortedByDescending { it.lastModified })
            combinedFiles.addAll(excludedFiles.sortedByDescending { it.lastModified })

            if (combinedFiles.isEmpty()) return Pair(result, 0)

            val isOnlyAudio = folder.mediaType == context.getString(R.string.only_audio)
            if (isOnlyAudio) {
                result.add(PreviewData(identifier = "audio_count", isCountOnly = true, count = totalCount))
                return Pair(result, totalCount)
            }

            val displayLimit = calculateDisplayLimit(context)

            if (combinedFiles.size <= displayLimit) {
                combinedFiles.forEach { fileInfo ->
                    val bitmap = loadFileThumbnailFast(context, fileInfo.uri, fileInfo.isVideo)
                    result.add(PreviewData(identifier = fileInfo.identifier, bitmap = bitmap, isExcluded = fileInfo.isExcluded))
                }
            } else {
                combinedFiles.take(displayLimit - 1).forEach { fileInfo ->
                    val bitmap = loadFileThumbnailFast(context, fileInfo.uri, fileInfo.isVideo)
                    result.add(PreviewData(identifier = fileInfo.identifier, bitmap = bitmap, isExcluded = fileInfo.isExcluded))
                }
                result.add(PreviewData(identifier = "more_count", isCountOnly = true, count = totalCount - (result.count { !it.isExcluded })))
            }

            return Pair(result, totalCount)
        }

        private fun calculateDisplayLimit(context: Context): Int {
            val displayMetrics = context.resources.displayMetrics
            val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
            val availableWidthDp = (screenWidthDp - 64) + 8
            val limit = (availableWidthDp / 56).toInt()
            return limit.coerceAtLeast(2)
        }

        private fun loadFileThumbnailFast(context: Context, uri: Uri, isVideo: Boolean): Bitmap? {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    return context.contentResolver.loadThumbnail(uri, Size(150, 150), null)
                }
            } catch (e: Exception) {}

            if (isVideo) {
                return getVideoFrame(context, uri)
            }
            return null
        }

        private fun getVideoFrame(context: Context, uri: Uri): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
    }

    private data class FileInfo(
        val uri: Uri,
        val name: String,
        val lastModified: Long,
        val identifier: String,
        val isVideo: Boolean,
        val isExcluded: Boolean
    )

    data class PreviewData(
        val identifier: String,
        val bitmap: Bitmap? = null,
        val isCountOnly: Boolean = false,
        val count: Int = 0,
        val isExcluded: Boolean = false
    )

    companion object {
        const val ACTION_PREVIEWS_UPDATED = "com.example.photouploaderapp.PREVIEWS_UPDATED"
    }
}
