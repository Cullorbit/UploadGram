package com.example.photouploaderapp.configs

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.databinding.GalleryAudioItemBinding

class GalleryAudioAdapter(
    private val context: Context,
    private val items: List<GalleryMediaItem>,
    private val onMediaClick: (GalleryMediaItem) -> Unit,
    private val onSyncStateChanged: () -> Unit
) : RecyclerView.Adapter<GalleryAudioAdapter.AudioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val binding = GalleryAudioItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AudioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class AudioViewHolder(private val binding: GalleryAudioItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GalleryMediaItem) {
            binding.tvAudioName.text = item.name
            binding.root.alpha = if (item.isExcluded) 0.5f else 1.0f

            binding.cbSync.setOnCheckedChangeListener(null)
            binding.cbSync.isChecked = !item.isExcluded

            binding.cbSync.setOnCheckedChangeListener { _, isChecked ->
                val isExcluded = !isChecked
                item.isExcluded = isExcluded
                binding.root.alpha = if (isExcluded) 0.5f else 1.0f

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
}
