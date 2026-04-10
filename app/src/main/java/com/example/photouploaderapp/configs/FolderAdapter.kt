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

    private var itemClickListener: OnItemClickListener? = null
    private var deleteClickListener: OnDeleteClickListener? = null
    private var resetCacheClickListener: OnResetCacheClickListener? = null
    private var syncToggleListener: OnSyncToggleListener? = null

    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    fun onDetach() {
        adapterScope.cancel()
    }

    inner class FolderViewHolder(private val binding: FolderItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null
        private var lastPreviewsIdentifiers: List<String> = emptyList()
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
            
            // Если ViewHolder переиспользуется для другой папки, сбрасываем кэш превью
            if (lastFolderId != folder.id) {
                lastFolderId = folder.id
                lastPreviewsIdentifiers = emptyList()
                lastTotalCount = -1
            }
            
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

            loadPreviews(folder)
        }

        private fun loadPreviews(folder: Folder) {
            job?.cancel()
            
            job = adapterScope.launch {
                val (newPreviews, totalFound) = withContext(Dispatchers.IO) {
                    getFolderPreviewsFast(itemView.context, folder)
                }
                
                val currentIdentifiers = newPreviews.map { it.identifier }
                
                // Проверяем и идентификаторы превью, и общее количество файлов
                if (currentIdentifiers == lastPreviewsIdentifiers && totalFound == lastTotalCount && lastPreviewsIdentifiers.isNotEmpty()) {
                    return@launch
                }
                
                lastPreviewsIdentifiers = currentIdentifiers
                lastTotalCount = totalFound

                // Запускаем анимацию
                val transition = AutoTransition().apply {
                    duration = 300
                    ordering = AutoTransition.ORDERING_TOGETHER
                }
                TransitionManager.beginDelayedTransition(binding.llPreviews, transition)

                updatePreviewContainer(newPreviews)
            }
        }

        private fun updatePreviewContainer(newPreviews: List<PreviewData>) {
            val container = binding.llPreviews
            val inflater = LayoutInflater.from(container.context)

            val currentViewsMap = container.children.associateBy { it.tag as? String }
            container.removeAllViews()

            if (newPreviews.isEmpty()) {
                addEmptyPreview(container, inflater)
                return
            }

            newPreviews.forEach { data ->
                val existingView = currentViewsMap[data.identifier]
                
                if (existingView != null) {
                    // Обновляем текст счетчика, если это элемент-заглушка с количеством
                    if (data.isCountOnly) {
                        existingView.findViewById<TextView>(R.id.tvMoreCount)?.text = if (data.identifier == "audio_count") "${data.count}" else "+${data.count}"
                    }
                    container.addView(existingView)
                } else {
                    val previewBinding = PreviewItemBinding.inflate(inflater, container, false)
                    setupPreviewView(previewBinding, data)
                    previewBinding.root.tag = data.identifier
                    container.addView(previewBinding.root)
                }
            }
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
            val validFiles = mutableListOf<FileInfo>()

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
                            validFiles.add(FileInfo(
                                uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                                name = name,
                                lastModified = lastModified,
                                identifier = fileIdentifier,
                                isVideo = mime.startsWith("video/")
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FolderAdapter", "Fast scan error", e)
                return Pair(emptyList(), 0)
            }

            val totalCount = validFiles.size
            if (validFiles.isEmpty()) return Pair(result, 0)

            validFiles.sortByDescending { it.lastModified }

            val isOnlyAudio = folder.mediaType == context.getString(R.string.only_audio)
            if (isOnlyAudio) {
                result.add(PreviewData(identifier = "audio_count", isCountOnly = true, count = totalCount))
                return Pair(result, totalCount)
            }

            val displayLimit = calculateDisplayLimit(context)

            if (totalCount <= displayLimit) {
                validFiles.forEach { fileInfo ->
                    val bitmap = loadFileThumbnailFast(context, fileInfo.uri, fileInfo.isVideo)
                    result.add(PreviewData(identifier = fileInfo.identifier, bitmap = bitmap))
                }
            } else {
                validFiles.take(displayLimit - 1).forEach { fileInfo ->
                    val bitmap = loadFileThumbnailFast(context, fileInfo.uri, fileInfo.isVideo)
                    result.add(PreviewData(identifier = fileInfo.identifier, bitmap = bitmap))
                }
                result.add(PreviewData(identifier = "more_count", isCountOnly = true, count = totalCount - (displayLimit - 1)))
            }

            return Pair(result, totalCount)
        }

        private fun calculateDisplayLimit(context: Context): Int {
            val displayMetrics = context.resources.displayMetrics
            val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
            // Учитываем: 16dp (отступ карточки) * 2 + 16dp (внутренний padding) * 2 = 64dp
            // Мы добавили negative margin -8dp для ScrollView, поэтому эффективная доступная ширина на 8dp больше.
            val availableWidthDp = (screenWidthDp - 64) + 8
            // Каждая плитка: 48dp (ширина) + 8dp (отступ справа) = 56dp
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
        val isVideo: Boolean
    )

    data class PreviewData(
        val identifier: String,
        val bitmap: Bitmap? = null,
        val isCountOnly: Boolean = false,
        val count: Int = 0
    )

    companion object {
        const val ACTION_PREVIEWS_UPDATED = "com.example.photouploaderapp.PREVIEWS_UPDATED"
    }
}
