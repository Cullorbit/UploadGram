package com.example.photouploaderapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.configs.*
import com.example.photouploaderapp.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.*

class MainActivity : AppCompatActivity(), AddFolderDialog.AddFolderListener, EditFolderDialog.EditFolderListener {

    private var isServiceRunning = false
    lateinit var binding: ActivityMainBinding
    private val folders = mutableListOf<Folder>()
    private val folderAdapter by lazy { FolderAdapter(folders) }
    private lateinit var logHelper: LogHelper
    private lateinit var settingsManager: SettingsManager
    private lateinit var serviceController: ServiceController
    private lateinit var uiUpdater: UIUpdater
    private lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, getString(R.string.notification_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(LogHelper.EXTRA_MESSAGE)
            if (message != null) {
                logHelper.appendLog(message)
            }
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isRunning = intent?.getBooleanExtra("is_running", false) ?: false
            updateServiceState(isRunning)
        }
    }

    private val previewsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshPreviews()
        }
    }

    private val topicErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val folderName = intent?.getStringExtra("folder_name")
            if (folderName != null) {
                handleTopicError(folderName)
            }
        }
    }

    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("Folders", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(settingsManager.themeMode)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initControllers()
        setupListeners()
        loadData()
        setupAppVersion()
        checkForUpdates()
        checkNotificationPermission()
        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val layoutId = if (viewType == 0) R.layout.page_folders else R.layout.page_log
                val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
                return object : RecyclerView.ViewHolder(view) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                if (position == 0) {
                    val rv = holder.itemView.findViewById<RecyclerView>(R.id.recyclerViewFolders)
                    rv.layoutManager = LinearLayoutManager(this@MainActivity)
                    rv.adapter = folderAdapter
                } else {
                    val tvLog = holder.itemView.findViewById<TextView>(R.id.tvLog)
                    val scrollView = holder.itemView.findViewById<ScrollView>(R.id.scrollViewLog)
                    logHelper.updateViews(tvLog, scrollView)
                }
            }

            override fun getItemCount(): Int = 2
            override fun getItemViewType(position: Int): Int = position
        }

        binding.viewPager.adapter = adapter
        
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) getString(R.string.tab_folders) else getString(R.string.tab_log)
        }.attach()

        binding.viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    binding.btnAddFolderIconButton.visibility = View.VISIBLE
                    binding.btnClearLogIconButton.visibility = View.GONE
                    binding.buttonSpacer.visibility = View.GONE
                } else {
                    binding.btnAddFolderIconButton.visibility = View.GONE
                    binding.btnClearLogIconButton.visibility = View.VISIBLE
                    binding.buttonSpacer.visibility = View.VISIBLE
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun checkNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun showAddFolderDialog() {
        val dialog = AddFolderDialog(settingsManager)
        dialog.show(supportFragmentManager, "AddFolderDialog")
    }

    override fun onFolderAdded(folder: Folder) {
        folders.add(folder)
        folderAdapter.notifyItemInserted(folders.size - 1)
        saveFolders()
        LogHelper.writeLog(this, getString(R.string.new_folder_added, folder.name))
        stopServiceIfNeeded()
    }

    override fun onFolderEdited(editedFolder: Folder) {
        val position = folders.indexOfFirst { it.id == editedFolder.id }
        if (position != -1) {
            val hasChanged = folders[position] != editedFolder
            folders[position] = editedFolder
            folderAdapter.notifyItemChanged(position)
            saveFolders()
            if (hasChanged) {
                stopServiceIfNeeded()
            }
        }
        refreshPreviews()
    }

    private fun initControllers() {
        logHelper = LogHelper(this)
        serviceController = ServiceController(this, settingsManager)
        uiUpdater = UIUpdater(this, settingsManager)
    }

    private fun setupListeners() {
        setupToolbar()

        binding.btnAddFolderIconButton.setOnClickListener { 
            if (!settingsManager.chatId.isNullOrEmpty()) {
                showAddFolderDialog()
            } else {
                val msg = getString(R.string.configure_bot_token_chat_id)
                LogHelper.writeLog(this, msg)
                showToast(msg)
            }
        }
        binding.btnClearLogIconButton.setOnClickListener { logHelper.clearLog() }

        binding.btnToggleService.setOnClickListener {
            if (isServiceRunning) {
                serviceController.stopService()
            } else {
                if (settingsManager.chatId.isNullOrEmpty()) {
                    val msg = getString(R.string.configure_bot_token_chat_id)
                    LogHelper.writeLog(this, msg)
                    showToast(msg)
                    return@setOnClickListener
                }
                if (folders.none { it.isSyncing }) {
                    LogHelper.writeLog(this, getString(R.string.no_folders_for_sync))
                    return@setOnClickListener
                }
                serviceController.startService(folders)
            }
            refreshPreviews()
        }

        setupAdapterListeners()

        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {}
        }
    }

    private fun setupAppVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionText = getString(R.string.app_version_format, versionName)
            binding.tvAppVersion.text = versionText
        } catch (e: Exception) {
            Log.e("MainActivity", getString(R.string.error_get_version), e)
        }
    }

    private fun checkForUpdates() {
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            null
        } ?: "0.0.0"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://api.github.com/repos/Cullorbit/UploadGram/tags")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@launch

                    val jsonData = response.body?.string() ?: return@launch
                    val jsonArray = JSONArray(jsonData)
                    if (jsonArray.length() > 0) {
                        val latestTag = jsonArray.getJSONObject(0).getString("name")
                        val cleanLatestTag = latestTag.trimStart('v')

                        if (isNewerVersion(currentVersion, cleanLatestTag)) {
                            withContext(Dispatchers.Main) {
                                showUpdateAvailable(latestTag)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Update check failed", e)
            }
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val curr = currentParts.getOrElse(i) { 0 }
            val lat = latestParts.getOrElse(i) { 0 }
            if (lat > curr) return true
            if (lat < curr) return false
        }
        return false
    }

    private fun showUpdateAvailable(latestVersion: String) {
        binding.tvAppVersion.apply {
            text = getString(R.string.update_available, latestVersion)
            setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Cullorbit/UploadGram/releases"))
                startActivity(intent)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupAdapterListeners() {
        folderAdapter.setOnItemClickListener(object : FolderAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                if (!settingsManager.chatId.isNullOrEmpty()) {
                    showEditFolderDialog(position)
                }
            }
        })

        folderAdapter.setOnDeleteClickListener(object : FolderAdapter.OnDeleteClickListener {
            override fun onDeleteClick(position: Int) {
                if (position in folders.indices) {
                    val folder = folders[position]
                    showDeleteFolderConfirmationDialog(position, folder)
                }
            }
        })

        folderAdapter.setOnResetCacheClickListener(object : FolderAdapter.OnResetCacheClickListener {
            override fun onResetCacheClick(position: Int) {
                if (position in folders.indices) {
                    val folder = folders[position]
                    showResetCacheConfirmationDialog(folder)
                }
            }
        })

        folderAdapter.setOnSyncToggleListener(object : FolderAdapter.OnSyncToggleListener {
            override fun onSyncToggle(position: Int, isChecked: Boolean) {
                if (position in folders.indices) {
                    folders[position].isSyncing = isChecked
                    saveFolders()
                    stopServiceIfNeeded(getString(R.string.folder_changes_detected))
                    refreshPreviews()
                }
            }
        })

        folderAdapter.setOnShowAllPreviewsListener(object : FolderAdapter.OnShowAllPreviewsListener {
            override fun onShowAllPreviews(folder: Folder, shownIdentifiers: List<String>) {
                showAllPreviewsDialog(folder, shownIdentifiers)
            }
        })
    }

    private fun showAllPreviewsDialog(folder: Folder, shownIdentifiers: List<String>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_all_previews, null)
        val rvPreviews = dialogView.findViewById<RecyclerView>(R.id.rvAllPreviews)
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(folder.name)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save), null)
            .setNeutralButton(R.string.deselect_all, null) 
            .create()

        val adapter = FullPreviewAdapter(this, folder, shownIdentifiers) { fullPreviewAdapter ->
            folderAdapter.notifyDataSetChanged()
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)?.let { btn ->
                updateSelectAllButtonText(btn, fullPreviewAdapter.isAllExcluded())
            }
        }
        
        rvPreviews.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 2)
        rvPreviews.adapter = adapter
        
        dialog.setOnShowListener {
            val neutralButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL)
            updateSelectAllButtonText(neutralButton, adapter.isAllExcluded())
            
            neutralButton.setOnClickListener {
                val shouldExcludeAll = !adapter.isAllExcluded()
                adapter.toggleAll(shouldExcludeAll)
                updateSelectAllButtonText(neutralButton, shouldExcludeAll)
            }
        }
        
        dialog.setOnDismissListener {
            adapter.onDetach()
        }
        
        dialog.show()
    }

    private fun updateSelectAllButtonText(button: android.widget.Button?, allExcluded: Boolean) {
        button?.text = if (allExcluded) getString(R.string.select_all) else getString(R.string.deselect_all)
    }

    private fun showDeleteFolderConfirmationDialog(position: Int, folder: Folder) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.delete_folder_title))
            .setMessage(getString(R.string.delete_folder_message, folder.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                val removedFolderName = folder.name
                folders.removeAt(position)
                folderAdapter.notifyItemRemoved(position)
                folderAdapter.notifyItemRangeChanged(position, folders.size)
                saveFolders()
                LogHelper.writeLog(this@MainActivity, getString(R.string.folder_deleted, removedFolderName))
                stopServiceIfNeeded()
                refreshPreviews()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showResetCacheConfirmationDialog(folder: Folder) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.reset_cache_title))
            .setMessage(getString(R.string.reset_cache_message, folder.name))
            .setPositiveButton(getString(R.string.reset_cache_confirm)) { _, _ ->
                resetSentFilesCacheForFolder(folder)
                LogHelper.writeLog(this@MainActivity, getString(R.string.cache_cleared_for_folder, folder.name))
                stopServiceIfNeeded(getString(R.string.service_stopped_due_to_cache_reset))
                refreshPreviews()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun resetSentFilesCacheForFolder(folder: Folder) {
        val sentFilesPrefs = getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        val allEntries = sentFilesPrefs.all
        val folderIdPrefix = "folder_${folder.id}_"
        sentFilesPrefs.edit().apply {
            allEntries.keys.forEach { key ->
                if (key.startsWith(folderIdPrefix)) {
                    remove(key)
                }
            }
            apply()
        }
    }

    private fun showEditFolderDialog(position: Int) {
        val folder = folders[position]
        val dialog = EditFolderDialog(settingsManager, folder)
        dialog.show(supportFragmentManager, "EditFolderDialog")
    }

    private fun handleTopicError(folderName: String) {
        serviceController.stopService()
        var found = false
        folders.forEachIndexed { index, folder ->
            if (folder.name == folderName) {
                folder.hasTopicError = true
                folder.isSyncing = false
                folderAdapter.notifyItemChanged(index)
                found = true
            }
        }
        if (found) {
            saveFolders()
            showToast(getString(R.string.error_topic_not_found_message, folderName))
        }
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun loadData() {
        val json = sharedPreferences.getString("folders", null)
        if (json != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<Folder>>() {}.type
            val loadedFolders: List<Folder> = com.google.gson.Gson().fromJson(json, type)
            folders.clear()
            folders.addAll(loadedFolders)
            folderAdapter.notifyDataSetChanged()
        }
    }

    private fun updateServiceState(isRunning: Boolean) {
        isServiceRunning = isRunning
        binding.btnToggleService.text = if (isRunning) {
            getString(R.string.stop_service)
        } else {
            getString(R.string.start_service)
        }
    }

    fun stopServiceIfNeeded(logMessage: String? = null) {
        if (isServiceRunning) {
            logMessage?.let { LogHelper.writeLog(this, it) }
            serviceController.stopService()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun refreshPreviews() {
        folderAdapter.notifyDataSetChanged()
    }

    private fun saveFolders() {
        val json = com.google.gson.Gson().toJson(folders)
        sharedPreferences.edit().putString("folders", json).apply()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            logReceiver, IntentFilter(LogHelper.ACTION_LOG_UPDATED)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            serviceStateReceiver, IntentFilter("com.example.photouploaderapp.SERVICE_STATE_CHANGED")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            previewsUpdateReceiver, IntentFilter(FolderAdapter.ACTION_PREVIEWS_UPDATED)
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            topicErrorReceiver, IntentFilter("com.example.photouploaderapp.TOPIC_ERROR")
        )
        logHelper.loadSavedLog()
        refreshPreviews()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStateReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(previewsUpdateReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(topicErrorReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        folderAdapter.onDetach()
    }
}
