package com.example.photouploaderapp.configs

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.photouploaderapp.R
import com.example.photouploaderapp.databinding.DialogAddFolderBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AddFolderDialog(
    private val settingsManager: SettingsManager,
) : DialogFragment() {

    interface AddFolderListener {
        fun onFolderAdded(folder: Folder)
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

    private var topicsMap = mutableMapOf<String, String>() 

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAddFolderBinding.inflate(LayoutInflater.from(context))
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setView(binding.root)
            .setTitle(getString(R.string.add_folder))
            .setPositiveButton(getString(R.string.save), null)
            .setNegativeButton(getString(R.string.cancel), null)

        setupViews()
        loadTopics()

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val folderName = binding.etFolderName.text.toString()
                
                val topicId = if (binding.tilManualTopicId.visibility == View.VISIBLE) {
                    binding.etManualTopicId.text.toString().trim()
                } else {
                    val selectedTopicName = binding.etTopicNumber.text.toString()
                    topicsMap[selectedTopicName] ?: ""
                }
                
                if (folderName.isBlank() || selectedFolderPath == null) {
                    Toast.makeText(context, getString(R.string.field_cannot_be_empty), Toast.LENGTH_SHORT).show()
                } else {
                    validateAndAdd(folderName, topicId, dialog)
                }
            }
        }

        return dialog
    }

    private fun loadTopics() {
        val chatId = settingsManager.chatId ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            
            try {
                val chatUrl = "${settingsManager.proxyUrl}/bot${settingsManager.botToken}/getChat?chat_id=$chatId"
                val chatResponse = client.newCall(Request.Builder().url(chatUrl).build()).execute()
                val chatBody = chatResponse.body?.string() ?: "{}"
                val chatJson = JSONObject(chatBody)
                
                if (!chatJson.optBoolean("ok", false)) {
                    val desc = chatJson.optString("description", getString(R.string.error_get_chat))
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), getString(R.string.error_chat_with_desc, desc), Toast.LENGTH_LONG).show()
                        binding.tilTopicNumber.visibility = View.GONE
                        binding.tilManualTopicId.visibility = View.GONE
                    }
                    return@launch
                }

                val result = chatJson.optJSONObject("result")
                val isForum = result?.optBoolean("is_forum", false) ?: false
                
                if (!isForum) {
                    withContext(Dispatchers.Main) {
                        binding.tilTopicNumber.visibility = View.GONE
                        binding.tilManualTopicId.visibility = View.GONE
                    }
                    return@launch
                }

                val url = "${settingsManager.proxyUrl}/bot${settingsManager.botToken}/getForumTopics?chat_id=$chatId"
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val jsonData = response.body?.string()
                    val jsonObject = JSONObject(jsonData ?: "{}")
                    
                    val ok = jsonObject.optBoolean("ok", false)
                    if (ok) {
                        val resObj = jsonObject.optJSONObject("result")
                        val topics = resObj?.optJSONArray("topics")
                        
                        if (topics != null && topics.length() > 0) {
                            topicsMap.clear()
                            val topicNames = mutableListOf<String>()
                            val mainChatName = getString(R.string.main_chat_no_topic)
                            topicNames.add(mainChatName)
                            topicsMap[mainChatName] = ""

                            for (i in 0 until topics.length()) {
                                val topic = topics.getJSONObject(i)
                                val name = topic.optString("name")
                                val id = topic.optLong("message_thread_id").toString()
                                topicsMap[name] = id
                                topicNames.add(name)
                            }

                            withContext(Dispatchers.Main) {
                                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, topicNames)
                                binding.etTopicNumber.setAdapter(adapter)
                                binding.tilTopicNumber.visibility = View.VISIBLE
                                binding.tilManualTopicId.visibility = View.GONE
                                binding.tilTopicNumber.helperText = getString(R.string.topics_loaded)
                                binding.tilTopicNumber.error = null
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                binding.tilTopicNumber.visibility = View.GONE
                                binding.tilManualTopicId.visibility = View.GONE
                            }
                        }
                    } else {
                        val description = jsonObject.optString("description", getString(R.string.error_topics))
                        withContext(Dispatchers.Main) {
                            if (description.contains("method not found", ignoreCase = true)) {
                                binding.tilTopicNumber.visibility = View.GONE
                                binding.tilManualTopicId.visibility = View.VISIBLE
                            } else {
                                binding.tilTopicNumber.visibility = View.VISIBLE
                                binding.tilManualTopicId.visibility = View.GONE
                                binding.tilTopicNumber.error = description
                                Toast.makeText(requireContext(), getString(R.string.error_loading_topics, description), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tilTopicNumber.error = getString(R.string.network_error)
                    Toast.makeText(requireContext(), getString(R.string.network_error_loading_topics), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun validateAndAdd(folderName: String, topicId: String, dialog: Dialog) {
        val chatId = settingsManager.chatId
        if (chatId.isNullOrEmpty()) {
            Toast.makeText(context, getString(R.string.configure_bot_token_chat_id), Toast.LENGTH_SHORT).show()
            return
        }

        val targetTil = if (binding.tilManualTopicId.visibility == View.VISIBLE) binding.tilManualTopicId else binding.tilTopicNumber
        targetTil.helperText = getString(R.string.checking_topic)
        targetTil.error = null

        lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            
            try {
                val checkUrl = "${settingsManager.proxyUrl}/bot${settingsManager.botToken}/getChat?chat_id=$chatId"
                val request = Request.Builder().url(checkUrl).build()
                client.newCall(request).execute().use { response ->
                    val jsonData = response.body?.string()
                    val jsonObject = JSONObject(jsonData ?: "{}")
                    val ok = jsonObject.optBoolean("ok", false)

                    if (!ok) {
                        withContext(Dispatchers.Main) {
                            targetTil.error = getString(R.string.bot_not_in_chat)
                        }
                        return@launch
                    }

                    if (topicId.isNotEmpty()) {
                        val topicCheckUrl = "${settingsManager.proxyUrl}/bot${settingsManager.botToken}/sendChatAction?chat_id=$chatId&message_thread_id=$topicId&action=typing"
                        val topicRequest = Request.Builder().url(topicCheckUrl).build()
                        client.newCall(topicRequest).execute().use { topicResponse ->
                            val topicJsonData = topicResponse.body?.string()
                            val topicJsonObject = JSONObject(topicJsonData ?: "{}")
                            val topicOk = topicJsonObject.optBoolean("ok", false)

                            withContext(Dispatchers.Main) {
                                if (topicOk) {
                                    addFolderAndDismiss(folderName, topicId, dialog)
                                } else {
                                    val errorDesc = topicJsonObject.optString("description", "")
                                    if (errorDesc.contains("thread not found", ignoreCase = true)) {
                                        targetTil.error = getString(R.string.topic_not_found, topicId)
                                    } else {
                                        targetTil.error = getString(R.string.error_topic_with_desc, errorDesc)
                                    }
                                }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            addFolderAndDismiss(folderName, topicId, dialog)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    targetTil.error = getString(R.string.error_checking, e.message ?: "")
                }
            }
        }
    }

    private fun addFolderAndDismiss(folderName: String, topicId: String, dialog: Dialog) {
        val mediaType = binding.spinnerMediaType.text.toString()
        settingsManager.selectedMediaType = mediaType

        val folder = Folder(
            name = folderName,
            topic = topicId,
            mediaType = mediaType,
            path = selectedFolderPath.toString(),
            isSyncing = true
        )
        listener.onFolderAdded(folder)
        dialog.dismiss()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val windowToken = dialog?.window?.currentFocus?.windowToken ?: binding.root.windowToken
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    private fun setupViews() {
        val mediaTypes = resources.getStringArray(R.array.media_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mediaTypes)
        binding.spinnerMediaType.setAdapter(adapter)

        binding.spinnerMediaType.setOnClickListener { hideKeyboard() }
        binding.spinnerMediaType.setOnItemClickListener { _, _, _, _ ->
            hideKeyboard()
        }

        binding.etTopicNumber.setOnClickListener { hideKeyboard() }
        binding.etTopicNumber.setOnItemClickListener { _, _, _, _ ->
            hideKeyboard()
        }

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
