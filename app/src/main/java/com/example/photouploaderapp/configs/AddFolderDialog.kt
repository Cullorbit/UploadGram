package com.example.photouploaderapp.configs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import com.example.photouploaderapp.R

class AddFolderDialog(private val settingsManager: SettingsManager, private val listener: AddFolderListener) : DialogFragment() {

    interface AddFolderListener {
        fun onFolderAdded(folder: Folder)
    }

    private lateinit var folderPath: EditText
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                // Важно! Сохраняем персистентное разрешение на доступ к папке
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                folderPath.setText(uri.toString())
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view: View = LayoutInflater.from(context).inflate(R.layout.dialog_add_folder, null)

        val folderName = view.findViewById<EditText>(R.id.etFolderName)
        val topicNumber = view.findViewById<EditText>(R.id.etTopicNumber)
        val mediaType = view.findViewById<Spinner>(R.id.spinnerMediaType)
        folderPath = view.findViewById(R.id.etFolderPath)
        val btnSelectFolder = view.findViewById<Button>(R.id.btnSelectFolder)

        val mediaTypes = resources.getStringArray(R.array.media_types)
        mediaType.setSelection(mediaTypes.indexOf(settingsManager.selectedMediaType)) // Устанавливаем текущее значение

        btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        return AlertDialog.Builder(requireContext())
            .setView(view)
            .setTitle(getString(R.string.add_folder))
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val folder = Folder(
                    folderName.text.toString(),
                    topicNumber.text.toString(),
                    mediaType.selectedItem.toString(),
                    folderPath.text.toString(),
                    isSyncing = true // <<< ВОТ ИСПРАВЛЕНИЕ! Новая папка сразу активна.
                )
                settingsManager.selectedMediaType = mediaType.selectedItem.toString() // Сохраняем выбранный тип медиа
                listener.onFolderAdded(folder)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
    }
}
