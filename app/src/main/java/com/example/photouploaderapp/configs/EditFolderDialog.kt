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
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.DialogAddFolderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EditFolderDialog(
    private val settingsManager: SettingsManager,
    private val folder: Folder
) : DialogFragment() {

    interface EditFolderListener {
        fun onFolderEdited(folder: Folder)
    }
    private lateinit var listener: EditFolderListener
    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            listener = context as EditFolderListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement EditFolderListener")
        }
    }

    private var _binding: DialogAddFolderBinding? = null
    private val binding get() = _binding!!

    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>
    private var selectedFolderPath: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedFolderPath = folder.path.toUri()

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
        _binding = DialogAddFolderBinding.inflate(LayoutInflater.from(context))
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setTitle(getString(R.string.edit_folder))

        setupViews()
        populateFields()

        builder
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val folderName = binding.etFolderName.text.toString()
                if (folderName.isBlank() || selectedFolderPath == null) {
                    Toast.makeText(context, getString(R.string.field_cannot_be_empty), Toast.LENGTH_SHORT).show()
                } else {
                    val editedFolder = folder.copy(
                        name = folderName,
                        topic = binding.etTopicNumber.text.toString(),
                        mediaType = binding.spinnerMediaType.text.toString(),
                        path = selectedFolderPath.toString()
                    )
                    listener.onFolderEdited(editedFolder)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)

        return builder.create()
    }

    private fun setupViews() {
        val mediaTypes = resources.getStringArray(R.array.media_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mediaTypes)
        binding.spinnerMediaType.setAdapter(adapter)

        binding.btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }
    }

    private fun populateFields() {
        binding.etFolderName.setText(folder.name)
        binding.etTopicNumber.setText(folder.topic)
        binding.etFolderPath.setText(folder.path)
        binding.spinnerMediaType.setText(folder.mediaType, false) // false - чтобы не фильтровать список
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
