package com.example.photouploaderapp.configs

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.viewpager2.widget.ViewPager2
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.DialogFullMediaViewerBinding

class FullMediaDialog(
    private val allItems: List<GalleryMediaItem>,
    private val initialItem: GalleryMediaItem,
    private val onStateChanged: () -> Unit
) : DialogFragment() {

    private var _binding: DialogFullMediaViewerBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                true
            } else {
                false
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogFullMediaViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = FullMediaPagerAdapter(requireContext(), allItems)
        binding.viewPagerMedia.adapter = adapter

        val initialIndex = allItems.indexOf(initialItem).coerceAtLeast(0)
        binding.viewPagerMedia.setCurrentItem(initialIndex, false)

        updatePageInfo(initialIndex)

        binding.viewPagerMedia.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updatePageInfo(position)
            }
        })
    }

    private fun updatePageInfo(position: Int) {
        if (position !in allItems.indices) return
        val currentItem = allItems[position]

        binding.tvFileName.text = currentItem.name
        binding.tvFileDetails.text = getString(R.string.folder_details, currentItem.folderName, getTypeName(currentItem))

        binding.cbFullSync.setOnCheckedChangeListener(null)
        binding.cbFullSync.isChecked = !currentItem.isExcluded

        binding.cbFullSync.setOnCheckedChangeListener { _, isChecked ->
            val isExcluded = !isChecked
            currentItem.isExcluded = isExcluded
            val context = requireContext()
            val excludedFilesPrefs = context.getSharedPreferences("ExcludedFiles", Context.MODE_PRIVATE)
            if (isExcluded) {
                excludedFilesPrefs.edit().putBoolean(currentItem.fileIdentifier, true).apply()
            } else {
                excludedFilesPrefs.edit().remove(currentItem.fileIdentifier).apply()
            }
            onStateChanged()
        }
    }

    private fun getTypeName(item: GalleryMediaItem): String {
        return when {
            item.isVideo -> getString(R.string.only_video)
            item.isAudio -> getString(R.string.only_audio)
            else -> getString(R.string.only_photo)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (binding.viewPagerMedia.adapter as? FullMediaPagerAdapter)?.releaseAll()
        _binding = null
    }
}
