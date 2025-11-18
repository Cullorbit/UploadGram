package com.example.photouploaderapp.configs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.DialogFragment
import com.example.photouploaderapp.R
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.photouploaderapp.telegrambot.UploadService

class EditFolderDialog(
    private val settingsManager: SettingsManager,
    private val folder: Folder,
    private val listener: EditFolderListener
) : DialogFragment() {

    interface EditFolderListener {
        fun onFolderEdited(folder: Folder)
    }

    private lateinit var folderPath: EditText
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
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

        // Устанавливаем начальные значения для редактируемой папки
        folderName.setText(folder.name)
        topicNumber.setText(folder.topic)
        folderPath.setText(folder.path)

        // Инициализируем список типов медиа
        val mediaTypes = resources.getStringArray(R.array.media_types)
        mediaType.setSelection(mediaTypes.indexOf(folder.mediaType))

        // Обработчик выбора папки
        btnSelectFolder.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        return AlertDialog.Builder(context)
            .setView(view)
            .setTitle("Редактировать папку")
            .setPositiveButton("Сохранить") { _, _ ->
                val editedFolder = Folder(
                    folderName.text.toString(),
                    topicNumber.text.toString(),
                    mediaType.selectedItem.toString(),
                    folderPath.text.toString(),
                    isSyncing = true,
                    botToken = settingsManager.botToken ?: "",
                    chatId = settingsManager.chatId ?: ""
                )

                resetFileStatus()

                listener.onFolderEdited(editedFolder)
            }
            .setNegativeButton("Отмена", null)
            .create()
    }

    private fun resetFileStatus() {
        UploadService.clearSentFilesForFolder(
            requireContext(),
            folder.name,
            folder.mediaType // Original media type before edit
        )
        Log.d("EditFolderDialog", "Cleared files for: ${folder.name}/${folder.mediaType}")
    }

    // Проверка на соответствие типа медиа
    private fun isValidMedia(file: DocumentFile): Boolean {
        val mediaType = folder.mediaType
        val fileName = file.name?.lowercase() ?: return false

        return when (mediaType) {
            "Фото" -> isImage(fileName)   // Если тип медиа "Фото", проверяем расширение на изображение
            "Видео" -> isVideo(fileName)  // Если тип медиа "Видео", проверяем расширение на видео
            else -> false
        }
    }

    // Проверка на изображение
    private fun isImage(fileName: String): Boolean {
        return fileName.endsWith(".jpg", true) || fileName.endsWith(".jpeg", true) ||
                fileName.endsWith(".png", true) || fileName.endsWith(".gif", true)
    }

    // Проверка на видео
    private fun isVideo(fileName: String): Boolean {
        return fileName.endsWith(".mp4", true) || fileName.endsWith(".avi", true) ||
                fileName.endsWith(".mkv", true) || fileName.endsWith(".mov", true) ||
                fileName.endsWith(".webm", true) || fileName.endsWith(".flv", true)
    }
}
