package com.example.photouploaderapp.configs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.DialogAddFolderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AddFolderDialog(
    private val settingsManager: SettingsManager,
) : DialogFragment() {

    interface AddFolderListener {fun onFolderAdded(folder: Folder)
    }
    private lateinit var listener: AddFolderListener
    private var _binding: DialogAddFolderBinding? = null
    private val binding get() = _binding!!

    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>
    private var selectedFolderPath: Uri? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as AddFolderListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement AddFolderListener")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedFolderPath = it
                binding.etFolderPath.setText(it.toString())
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())

        _binding = DialogAddFolderBinding.inflate(LayoutInflater.from(context))
        builder.setView(binding.root)

        setupViews()

        builder.setTitle(getString(R.string.add_folder))
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val folderName = binding.etFolderName.text.toString()
                if (folderName.isBlank() || selectedFolderPath == null) {
                    Toast.makeText(context, getString(R.string.field_cannot_be_empty), Toast.LENGTH_SHORT).show()
                } else {
                    val folder = Folder(
                        name = folderName,
                        topic = binding.etTopicNumber.text.toString(),
                        mediaType = binding.spinnerMediaType.text.toString(),
                        path = selectedFolderPath.toString(),
                        isSyncing = true
                    )
                    settingsManager.selectedMediaType = folder.mediaType
                    listener.onFolderAdded(folder)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)

        return builder.create()
    }

    private fun setupViews() {
        val mediaTypes = resources.getStringArray(R.array.media_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mediaTypes)
        binding.spinnerMediaType.setAdapter(adapter)

        val currentDefaultType = settingsManager.selectedMediaType
        if (mediaTypes.contains(currentDefaultType)) {
            binding.spinnerMediaType.setText(currentDefaultType, false)
        } else if (mediaTypes.isNotEmpty()) {
            binding.spinnerMediaType.setText(mediaTypes[0], false)
        }

        binding.btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
