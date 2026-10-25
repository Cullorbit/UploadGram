package com.example.photouploaderapp.configs

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.GalleryFolderSectionBinding
import com.example.photouploaderapp.databinding.GalleryMediaItemBinding
import kotlinx.coroutines.*

class GalleryFolderAdapter(
    private val context: Context,
    private val folders: List<Folder>,
    private val collapsedFolderIds: MutableSet<String>,
    private val onEditFolder: (Int) -> Unit,
    private val onDeleteFolder: (Int) -> Unit,
    private val onResetCache: (Int) -> Unit,
    private val onFolderSyncToggle: (Int, Boolean) -> Unit,
    private val onFolderExpandCollapse: (String, Boolean) -> Unit,
    private val onMediaClick: (GalleryMediaItem) -> Unit,
    private val onSyncStateChanged: () -> Unit
) : RecyclerView.Adapter<GalleryFolderAdapter.FolderSectionViewHolder>() {

    val folderSections = mutableListOf<FolderSection>()

    fun setFolderSections(sections: List<FolderSection>) {
        folderSections.clear()
        folderSections.addAll(sections)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderSectionViewHolder {
        val binding = GalleryFolderSectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return FolderSectionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FolderSectionViewHolder, position: Int) {
        holder.bind(folderSections[position], position)
    }

    override fun getItemCount(): Int = folderSections.size

    inner class FolderSectionViewHolder(private val binding: GalleryFolderSectionBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(section: FolderSection, position: Int) {
            binding.tvFolderName.text = section.folder.name
            binding.tvFolderDetails.text = context.getString(R.string.folder_details, section.folder.topic.ifEmpty { "-" }, section.folder.mediaType)

            binding.cbFolderSyncToggle.setOnCheckedChangeListener(null)
            binding.cbFolderSyncToggle.isChecked = section.folder.isSyncing
            binding.rvFolderMedia.alpha = if (section.folder.isSyncing) 1.0f else 0.5f

            binding.cbFolderSyncToggle.setOnCheckedChangeListener { _, isChecked ->
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    section.folder.isSyncing = isChecked
                    binding.rvFolderMedia.alpha = if (isChecked) 1.0f else 0.5f
                    onFolderSyncToggle(adapterPosition, isChecked)
                }
            }

            binding.btnResetCache.setOnClickListener {
                onResetCache(position)
            }

            binding.btnEditFolder.setOnClickListener {
                onEditFolder(position)
            }

            binding.btnDeleteFolder.setOnClickListener {
                onDeleteFolder(position)
            }

            section.isExpanded = !collapsedFolderIds.contains(section.folder.id)
            updateExpansionUI(section)

            binding.btnExpandCollapse.setOnClickListener {
                section.isExpanded = !section.isExpanded
                if (section.isExpanded) {
                    collapsedFolderIds.remove(section.folder.id)
                } else {
                    collapsedFolderIds.add(section.folder.id)
                }
                onFolderExpandCollapse(section.folder.id, section.isExpanded)
                updateExpansionUI(section)
            }

            val isAudioFolder = section.folder.mediaType == context.getString(R.string.only_audio)
            if (isAudioFolder) {
                binding.rvFolderMedia.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                val audioAdapter = GalleryAudioAdapter(context, section.mediaItems, onMediaClick, onSyncStateChanged)
                binding.rvFolderMedia.adapter = audioAdapter
            } else {
                binding.rvFolderMedia.layoutManager = GridLayoutManager(context, 3).apply {
                    initialPrefetchItemCount = 6
                }
                binding.rvFolderMedia.isNestedScrollingEnabled = false
                val mediaAdapter = GalleryMediaAdapter(context, section.mediaItems, onMediaClick, onSyncStateChanged)
                binding.rvFolderMedia.adapter = mediaAdapter
            }
        }

        private fun updateExpansionUI(section: FolderSection) {
            binding.rvFolderMedia.visibility = if (section.isExpanded) View.VISIBLE else View.GONE
            binding.btnExpandCollapse.setIconResource(
                if (section.isExpanded) R.drawable.ic_arrow_down_vector else R.drawable.ic_arrow_right_vector
            )
        }
    }
}

class GalleryMediaAdapter(
    private val context: Context,
    private val items: List<GalleryMediaItem>,
    private val onMediaClick: (GalleryMediaItem) -> Unit,
    private val onSyncStateChanged: () -> Unit
) : RecyclerView.Adapter<GalleryMediaAdapter.MediaViewHolder>() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = GalleryMediaItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class MediaViewHolder(private val binding: GalleryMediaItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null

        fun bind(item: GalleryMediaItem) {
            binding.ivThumbnail.setImageBitmap(null)
            job?.cancel()
            job = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadThumbnail(context, item)
                }
                if (bitmap != null) {
                    binding.ivThumbnail.setImageBitmap(bitmap)
                } else {
                    binding.ivThumbnail.setImageResource(R.drawable.ic_folder)
                }
            }

            binding.ivTypeIcon.visibility = if (item.isVideo || item.isAudio) View.VISIBLE else View.GONE
            binding.vDimOverlay.visibility = if (item.isExcluded) View.VISIBLE else View.GONE

            binding.cbSync.setOnCheckedChangeListener(null)
            binding.cbSync.isChecked = !item.isExcluded

            binding.cbSync.setOnCheckedChangeListener { _, isChecked ->
                val isExcluded = !isChecked
                item.isExcluded = isExcluded
                binding.vDimOverlay.visibility = if (isExcluded) View.VISIBLE else View.GONE

                val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
                if (isExcluded) {
                    excludedFilesPrefs.edit().putBoolean(item.fileIdentifier, true).apply()
                } else {
                    excludedFilesPrefs.edit().remove(item.fileIdentifier).apply()
                }
                onSyncStateChanged()
            }

            binding.root.setOnClickListener {
                onMediaClick(item)
            }
        }
    }

    private fun loadThumbnail(context: Context, item: GalleryMediaItem): Bitmap? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val thumb = context.contentResolver.loadThumbnail(item.uri, Size(300, 300), null)
                if (thumb != null) return thumb
            }
        } catch (e: Exception) {}

        if (item.isVideo) {
            val retriever = MediaMetadataRetriever()
            return try {
                context.contentResolver.openFileDescriptor(item.uri, "r")?.use { pfd ->
                    retriever.setDataSource(pfd.fileDescriptor)
                    retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
            } catch (e: Exception) {
                null
            } finally {
                retriever.release()
            }
        } else if (!item.isAudio) {
            try {
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    val options = android.graphics.BitmapFactory.Options().apply {
                        inSampleSize = 4
                    }
                    return android.graphics.BitmapFactory.decodeStream(stream, null, options)
                }
            } catch (e: Exception) {}
        }
        return null
    }
}

data class FolderSection(
    val folder: Folder,
    val mediaItems: MutableList<GalleryMediaItem>,
    var isExpanded: Boolean = true
)

data class GalleryMediaItem(
    val uri: Uri,
    val name: String,
    val folderId: String,
    val folderName: String,
    val fileIdentifier: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val lastModified: Long,
    val size: Long,
    var isExcluded: Boolean
)
