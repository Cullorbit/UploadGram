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
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photouploaderapp.configs.AddFolderDialog
import com.example.photouploaderapp.configs.EditFolderDialog
import com.example.photouploaderapp.configs.Folder
import com.example.photouploaderapp.configs.FolderAdapter
import com.example.photouploaderapp.configs.LogHelper
import com.example.photouploaderapp.configs.MyApplication
import com.example.photouploaderapp.configs.NavigationHandler
import com.example.photouploaderapp.configs.ServiceController
import com.example.photouploaderapp.configs.SettingsManager
import com.example.photouploaderapp.configs.UIUpdater
import com.example.photouploaderapp.configs.SyncIntervalDialog
import com.example.photouploaderapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(),
    AddFolderDialog.AddFolderListener,
    EditFolderDialog.EditFolderListener {
    private var isServiceRunning = false
    lateinit var binding: ActivityMainBinding
    private val folders = mutableListOf<Folder>()
    private val folderAdapter = FolderAdapter(folders)
    private lateinit var logHelper: LogHelper

    private lateinit var settingsManager: SettingsManager
    private lateinit var serviceController: ServiceController
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var uiUpdater: UIUpdater

    lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("log_message") ?: return
            logHelper.log(message)
        }
    }

    private val sharedPreferences: SharedPreferences by lazy {
        getSharedPreferences("Folders", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (application as? MyApplication)?.folderAdapter = folderAdapter

        settingsManager = SettingsManager(this)

        initControllers()
        setupRecyclerView()
        setupListeners()
        setupAppVersion()
        loadData()
        serviceController.cancelPeriodicScan()

        updateServiceState(false)
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val toggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
    }

    override fun onFolderAdded(folder: Folder) {
        folders.add(folder)
        folderAdapter.notifyItemInserted(folders.size - 1)
        saveFolders()
        logHelper.log(getString(R.string.new_folder_added, folder.name))
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
    }

    private fun initControllers() {
        logHelper = LogHelper(this, binding.tvLog, binding.scrollViewLog)
        serviceController = ServiceController(this, settingsManager)
        uiUpdater = UIUpdater(this, settingsManager)
        navigationHandler = NavigationHandler(this, settingsManager, uiUpdater)
    }

    private fun setupRecyclerView() {
        binding.recyclerViewFolders.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewFolders.adapter = folderAdapter
    }

    private fun setupListeners() {
        setupToolbarAndDrawer()
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            navigationHandler.handleNavigationItemSelected(menuItem)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.btnAddFolderIconButton.setOnClickListener { showAddFolderDialog() }
        binding.btnClearLogIconButton.setOnClickListener { logHelper.clearLog() }

        binding.btnToggleService.setOnClickListener {
            if (isServiceRunning) {
                serviceController.stopService()
                updateServiceState(false)
            } else {
                if (settingsManager.botToken.isNullOrEmpty() || settingsManager.chatId.isNullOrEmpty()) {
                    logHelper.log(getString(R.string.configure_bot_token_chat_id))
                    return@setOnClickListener
                }
                if (folders.none { it.isSyncing }) {
                    logHelper.log(getString(R.string.no_folders_for_sync))
                    return@setOnClickListener
                }
                serviceController.startService(folders)
                updateServiceState(true)
            }
        }

        setupAdapterListeners()

        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                // This is a placeholder, as the launcher is used in dialogs.
            }
        }
    }

    private fun setupAppVersion() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            binding.tvAppVersion.text = "UploadGram Version: $versionName"
        } catch (e: Exception) {
            Log.e("MainActivity", "Couldn't get app version", e)
            binding.tvAppVersion.text = ""
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setupAdapterListeners() {
        folderAdapter.setOnItemClickListener(object : FolderAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                showEditFolderDialog(position)
            }
        })

        folderAdapter.setOnDeleteClickListener(object : FolderAdapter.OnDeleteClickListener {
            override fun onDeleteClick(position: Int) {
                if (position in folders.indices) {
                    val removedFolderName = folders[position].name
                    folders.removeAt(position)
                    folderAdapter.notifyItemRemoved(position)
                    folderAdapter.notifyItemRangeChanged(position, folders.size)
                    saveFolders()
                    logHelper.log(getString(R.string.folder_deleted, removedFolderName))
                    stopServiceIfNeeded()
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
                }
            }
        })
    }

    private fun showResetCacheConfirmationDialog(folder: Folder) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reset_cache_title))
            .setMessage(getString(R.string.reset_cache_message, folder.name))
            .setPositiveButton(getString(R.string.reset_cache_confirm)) { _, _ ->
                resetSentFilesCacheForFolder(folder)
                logHelper.log(getString(R.string.cache_cleared_for_folder, folder.name))
                stopServiceIfNeeded(getString(R.string.service_stopped_due_to_cache_reset))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun resetSentFilesCacheForFolder(folder: Folder) {
        val sentFilesPrefs = getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        val editor = sentFilesPrefs.edit()

        val folderUri = folder.path.toUri()
        val documentFolder = DocumentFile.fromTreeUri(this, folderUri)

        documentFolder?.listFiles()?.forEach { file ->
            if (isValidMediaForReset(file, folder.mediaType)) {
                editor.remove(file.uri.toString())
            }
        }
        editor.apply()
    }

    private fun isValidMediaForReset(file: DocumentFile, mediaType: String): Boolean {
        val fileName = file.name?.lowercase() ?: return false
        val isFilePhoto = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").any { fileName.endsWith(it) }
        val isFileVideo = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv").any { fileName.endsWith(it) }
        val isFileAudio = listOf(".mp3", ".m4a", ".ogg", ".wav", ".flac").any { fileName.endsWith(it) }

        val photoTypeString = getString(R.string.only_photo)
        val videoTypeString = getString(R.string.only_video)
        val audioTypeString = getString(R.string.only_audio)
        val allTypeString = getString(R.string.all_media)

        return when (mediaType) {
            photoTypeString -> isFilePhoto
            videoTypeString -> isFileVideo
            audioTypeString -> isFileAudio
            allTypeString -> isFilePhoto || isFileVideo || isFileAudio
            else -> false
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadData() {
        logHelper.clearSavedLog()
        loadFolders()
        folderAdapter.notifyDataSetChanged()
        uiUpdater.updateSettingsDisplay()
        if (settingsManager.botToken.isNullOrEmpty() || settingsManager.chatId.isNullOrEmpty()) {
            logHelper.log(getString(R.string.configure_bot_token_chat_id))
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, IntentFilter("com.example.photouploaderapp.UPLOAD_LOG"))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    internal fun showSyncIntervalDialog() {
        SyncIntervalDialog(settingsManager).show(supportFragmentManager, "SyncIntervalDialog")
    }

    private fun showAddFolderDialog() {
        AddFolderDialog(settingsManager).show(supportFragmentManager, "AddFolderDialog")
    }

    private fun showEditFolderDialog(position: Int) {
        val folder = folders[position]
        EditFolderDialog(settingsManager, folder).show(supportFragmentManager, "EditFolderDialog")
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveFolders() {
        val json = com.google.gson.Gson().toJson(folders)
        sharedPreferences.edit().putString("folders", json).apply()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadFolders() {
        val json = sharedPreferences.getString("folders", null)
        if (json != null) {
            val type = object : com.google.gson.reflect.TypeToken<MutableList<Folder>>() {}.type
            val loadedFolders: MutableList<Folder> = com.google.gson.Gson().fromJson(json, type)
            folders.clear()
            folders.addAll(loadedFolders)
            folderAdapter.notifyDataSetChanged()
        }
    }

    fun stopServiceIfNeeded(logMessage: String? = null) {
        if (isServiceRunning) {
            serviceController.stopService()
            updateServiceState(false)
            logMessage?.let { logHelper.log(it) }
        }
    }

    private fun updateServiceState(isRunning: Boolean) {
        isServiceRunning = isRunning
        binding.btnToggleService.text = if (isRunning) getString(R.string.stop_service) else getString(R.string.start_service)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
