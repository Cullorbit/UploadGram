package com.example.photouploaderapp.configs

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.FullPreviewItemBinding
import kotlinx.coroutines.*

class FullPreviewAdapter(
    private val context: Context,
    private val folder: Folder,
    private val excludedByIdentifiers: List<String>,
    private val onStateChanged: (FullPreviewAdapter) -> Unit
) : RecyclerView.Adapter<FullPreviewAdapter.ViewHolder>() {

    private val allFiles = mutableListOf<FileInfo>()
    private val displayFiles = mutableListOf<FileInfo>()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        loadFiles()
    }

    private fun loadFiles() {
        scope.launch {
            val loadedFiles = withContext(Dispatchers.IO) {
                getFilesFromFolder()
            }
            allFiles.clear()
            allFiles.addAll(loadedFiles)
            
            updateDisplayList()
            
            notifyDataSetChanged()
            onStateChanged(this@FullPreviewAdapter)
        }
    }

    private fun updateDisplayList() {
        displayFiles.clear()
        displayFiles.addAll(allFiles.filter { !excludedByIdentifiers.contains(it.identifier) })
    }

    private fun getFilesFromFolder(): List<FileInfo> {
        val result = mutableListOf<FileInfo>()
        val treeUri = Uri.parse(folder.path)
        val sentFilesPrefs = context.getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)

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

                    if (!sentFilesPrefs.contains(fileIdentifier) && 
                        MediaUtils.isValidMedia(context, name, folder.mediaType)) {
                        
                        result.add(FileInfo(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                            identifier = fileIdentifier,
                            isVideo = mime.startsWith("video/"),
                            lastModified = lastModified,
                            isExcluded = excludedFilesPrefs.contains(fileIdentifier)
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result.sortedByDescending { it.lastModified }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FullPreviewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(displayFiles[position])
    }

    override fun getItemCount() = displayFiles.size

    fun toggleAll(excludeAll: Boolean) {
        val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
        val editor = excludedFilesPrefs.edit()
        
        allFiles.forEach { file ->
            file.isExcluded = excludeAll
            if (excludeAll) {
                editor.putBoolean(file.identifier, true)
            } else {
                editor.remove(file.identifier)
            }
        }
        editor.apply()
        
        updateDisplayList()
        notifyDataSetChanged()
        onStateChanged(this)
    }

    fun isAllExcluded(): Boolean {
        if (allFiles.isEmpty()) return true
        return allFiles.all { it.isExcluded }
    }

    fun onDetach() {
        scope.cancel()
    }

    inner class ViewHolder(private val binding: FullPreviewItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null

        fun bind(file: FileInfo) {
            binding.ivPreview.setImageBitmap(null)
            updateExclusionState(file.isExcluded)

            job?.cancel()
            job = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadFileThumbnail(binding.root.context, file.uri, file.isVideo)
                }
                binding.ivPreview.setImageBitmap(bitmap)
            }

            binding.root.setOnClickListener {
                toggleExclusion(file)
            }
        }

        private fun updateExclusionState(isExcluded: Boolean) {
            binding.vExclusionOverlay.visibility = if (isExcluded) View.VISIBLE else View.GONE
            binding.ivCheck.visibility = View.GONE
        }

        private fun toggleExclusion(file: FileInfo) {
            val context = binding.root.context
            val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
            if (file.isExcluded) {
                excludedFilesPrefs.edit().remove(file.identifier).apply()
            } else {
                excludedFilesPrefs.edit().putBoolean(file.identifier, true).apply()
            }
            
            file.isExcluded = !file.isExcluded
            allFiles.find { it.identifier == file.identifier }?.isExcluded = file.isExcluded
            
            val index = displayFiles.indexOf(file)
            if (index != -1) {
                notifyItemChanged(index)
                onStateChanged(this@FullPreviewAdapter)
            }
        }
    }

    private fun loadFileThumbnail(context: Context, uri: Uri, isVideo: Boolean): Bitmap? {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                return context.contentResolver.loadThumbnail(uri, Size(400, 400), null)
            }
        } catch (e: Exception) {}

        if (isVideo) {
            val retriever = android.media.MediaMetadataRetriever()
            return try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        }
        return null
    }

    data class FileInfo(
        val uri: Uri,
        val identifier: String,
        val isVideo: Boolean,
        val lastModified: Long,
        var isExcluded: Boolean
    )
}
