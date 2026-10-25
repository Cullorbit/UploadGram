package com.example.photouploaderapp.configs

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.GalleryItemBinding
import kotlinx.coroutines.*

class GalleryAdapter(
    private val context: Context,
    private val onItemClick: (GalleryItem) -> Unit,
    private val onItemLongClick: (GalleryItem) -> Unit,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    val items = mutableListOf<GalleryItem>()
    var isSelectionMode: Boolean = false
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun setItems(newItems: List<GalleryItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun startSelectionMode(initialItem: GalleryItem) {
        isSelectionMode = true
        initialItem.isSelected = true
        notifyDataSetChanged()
        onSelectionChanged(getSelectedCount())
    }

    fun clearSelection() {
        isSelectionMode = false
        items.forEach { it.isSelected = false }
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun getSelectedCount(): Int = items.count { it.isSelected }

    fun toggleSelection(item: GalleryItem) {
        item.isSelected = !item.isSelected
        val count = getSelectedCount()
        if (count == 0) {
            isSelectionMode = false
        }
        notifyDataSetChanged()
        onSelectionChanged(count)
    }

    fun excludeSelected(context: Context) {
        val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
        val editor = excludedFilesPrefs.edit()
        items.filter { it.isSelected }.forEach { item ->
            item.isExcluded = true
            editor.putBoolean(item.fileIdentifier, true)
        }
        editor.apply()
        clearSelection()
    }

    fun includeSelected(context: Context) {
        val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
        val editor = excludedFilesPrefs.edit()
        items.filter { it.isSelected }.forEach { item ->
            item.isExcluded = false
            editor.remove(item.fileIdentifier)
        }
        editor.apply()
        clearSelection()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = GalleryItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun onDetach() {
        scope.cancel()
    }

    inner class GalleryViewHolder(private val binding: GalleryItemBinding) : RecyclerView.ViewHolder(binding.root) {
        private var job: Job? = null

        fun bind(item: GalleryItem) {
            binding.ivThumbnail.setImageBitmap(null)
            
            job?.cancel()
            job = scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    loadThumbnail(context, item)
                }
                if (bitmap != null) {
                    binding.ivThumbnail.setImageBitmap(bitmap)
                    binding.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_CROP
                    binding.ivThumbnail.clearColorFilter()
                    ImageViewCompat.setImageTintList(binding.ivThumbnail, null)
                } else {
                    binding.ivThumbnail.setImageResource(R.drawable.ic_folder)
                    binding.ivThumbnail.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                    ImageViewCompat.setImageTintList(
                        binding.ivThumbnail,
                        ColorStateList.valueOf(typedValue.data)
                    )
                }
            }

            if (item.isVideo || item.isAudio) {
                binding.ivTypeIcon.visibility = View.VISIBLE
            } else {
                binding.ivTypeIcon.visibility = View.GONE
            }

            if (isSelectionMode) {
                binding.ivCheckMark.visibility = View.VISIBLE
                if (item.isSelected) {
                    binding.ivCheckMark.setImageResource(R.drawable.ic_check_green)
                    binding.vDimOverlay.visibility = View.VISIBLE
                } else {
                    binding.ivCheckMark.setImageResource(R.drawable.ic_check_gray)
                    binding.vDimOverlay.visibility = if (item.isExcluded) View.VISIBLE else View.GONE
                }
            } else {
                binding.ivCheckMark.visibility = View.GONE
                binding.vDimOverlay.visibility = if (item.isExcluded) View.VISIBLE else View.GONE
            }

            binding.root.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item)
                } else {
                    onItemClick(item)
                }
            }

            binding.root.setOnLongClickListener {
                if (!isSelectionMode) {
                    startSelectionMode(item)
                    onItemLongClick(item)
                }
                true
            }
        }
    }

    private fun loadThumbnail(context: Context, item: GalleryItem): Bitmap? {
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

data class GalleryItem(
    val uri: Uri,
    val name: String,
    val folderId: String,
    val folderName: String,
    val fileIdentifier: String,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val lastModified: Long,
    val size: Long,
    var isExcluded: Boolean,
    var isSelected: Boolean = false
)
