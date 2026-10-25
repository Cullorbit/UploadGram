package com.example.photouploaderapp.configs

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.ItemFullMediaPageBinding
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

class FullMediaPagerAdapter(
    private val context: Context,
    private val items: List<GalleryMediaItem>
) : RecyclerView.Adapter<FullMediaPagerAdapter.PageViewHolder>() {

    private val activePlayers = mutableListOf<android.media.MediaPlayer>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemFullMediaPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.releaseResources()
    }

    override fun getItemCount(): Int = items.size

    fun releaseAll() {
        activePlayers.forEach { 
            try { 
                if (it.isPlaying) it.stop()
                it.release() 
            } catch (e: Exception) {} 
        }
        activePlayers.clear()
    }

    inner class PageViewHolder(private val binding: ItemFullMediaPageBinding) : RecyclerView.ViewHolder(binding.root) {
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private var mediaPlayer: android.media.MediaPlayer? = null
        private val handler = Handler(Looper.getMainLooper())
        private var updateRunnable: Runnable? = null

        fun bind(item: GalleryMediaItem) {
            releaseResources()

            binding.ivFullPreview.visibility = View.GONE
            binding.videoView.visibility = View.GONE
            binding.btnPlayOverlay.visibility = View.GONE
            binding.audioPlayerLayout.visibility = View.GONE

            if (item.isVideo) {
                binding.videoView.visibility = View.VISIBLE
                binding.videoView.setVideoURI(item.uri)
                binding.videoView.setOnPreparedListener { mp ->
                    mp.start()
                    binding.btnPlayOverlay.visibility = View.GONE
                    binding.btnPlayOverlay.setImageResource(R.drawable.ic_pause_vector)
                }

                val toggleVideo = {
                    if (binding.videoView.isPlaying) {
                        binding.videoView.pause()
                        binding.btnPlayOverlay.setImageResource(R.drawable.ic_play_vector)
                        binding.btnPlayOverlay.visibility = View.VISIBLE
                    } else {
                        binding.videoView.start()
                        binding.btnPlayOverlay.setImageResource(R.drawable.ic_pause_vector)
                        binding.btnPlayOverlay.visibility = View.GONE
                    }
                }

                binding.videoView.setOnClickListener { toggleVideo() }
                binding.btnPlayOverlay.setOnClickListener { toggleVideo() }
            } else if (item.isAudio) {
                binding.audioPlayerLayout.visibility = View.VISIBLE
                binding.tvAudioName.text = item.name
                
                try {
                    mediaPlayer = android.media.MediaPlayer.create(context, item.uri)?.apply {
                        activePlayers.add(this)
                        setOnCompletionListener {
                            binding.btnPlayPause.text = "Play"
                        }
                    }
                } catch (e: Exception) {}

                val duration = mediaPlayer?.duration ?: 0
                binding.seekBarAudio.max = duration
                binding.tvTotalTime.text = formatDuration(duration.toLong())
                binding.tvCurrentTime.text = "0:00"

                updateRunnable = object : Runnable {
                    val player = mediaPlayer
                    override fun run() {
                        if (player != null && player.isPlaying) {
                            val current = player.currentPosition
                            binding.seekBarAudio.progress = current
                            binding.tvCurrentTime.text = formatDuration(current.toLong())
                            handler.postDelayed(this, 500)
                        }
                    }
                }

                binding.btnPlayPause.setOnClickListener {
                    val player = mediaPlayer ?: return@setOnClickListener
                    if (player.isPlaying) {
                        player.pause()
                        binding.btnPlayPause.text = "Play"
                        handler.removeCallbacks(updateRunnable!!)
                    } else {
                        player.start()
                        binding.btnPlayPause.text = "Pause"
                        handler.post(updateRunnable!!)
                    }
                }

                binding.seekBarAudio.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            mediaPlayer?.seekTo(progress)
                            binding.tvCurrentTime.text = formatDuration(progress.toLong())
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })

            } else {
                binding.ivFullPreview.visibility = View.VISIBLE
                binding.ivFullPreview.setImageURI(null)
                try {
                    binding.ivFullPreview.setImageURI(item.uri)
                } catch (e: Exception) {
                    scope.launch {
                        val bitmap = withContext(Dispatchers.IO) {
                            loadFullResBitmap(context, item)
                        }
                        if (bitmap != null) {
                            binding.ivFullPreview.setImageBitmap(bitmap)
                        } else {
                            binding.ivFullPreview.setImageResource(R.drawable.ic_folder)
                        }
                    }
                }
            }
        }

        fun releaseResources() {
            scope.cancel()
            updateRunnable?.let { handler.removeCallbacks(it) }
            try {
                if (mediaPlayer != null) {
                    if (mediaPlayer!!.isPlaying) mediaPlayer!!.stop()
                    mediaPlayer!!.release()
                    activePlayers.remove(mediaPlayer)
                    mediaPlayer = null
                }
            } catch (e: Exception) {}
            try {
                binding.videoView.stopPlayback()
            } catch (e: Exception) {}
        }

        private fun formatDuration(millis: Long): String {
            val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
            return String.format(java.util.Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    private fun loadFullResBitmap(context: Context, item: GalleryMediaItem): Bitmap? {
        try {
            context.contentResolver.openInputStream(item.uri)?.use { stream ->
                return android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
