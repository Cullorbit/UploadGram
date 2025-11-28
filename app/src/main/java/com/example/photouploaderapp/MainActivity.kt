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
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.configs.*
import com.example.photouploaderapp.configsimport.SyncIntervalDialog
import com.example.photouploaderapp.databinding.ActivityMainBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.io.path.exists

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private val folders = mutableListOf<Folder>()
    private val folderAdapter = FolderAdapter(folders)
    private lateinit var logHelper: LogHelper
    private lateinit var recyclerViewFolders: RecyclerView
    private lateinit var fabAddFolder: FloatingActionButton

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

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (application as? MyApplication)?.folderAdapter = folderAdapter

        settingsManager = SettingsManager(this)

        initControllers()
        setupRecyclerView()
        setupListeners()
        loadData()

        binding.btnStartService.isEnabled = true
        binding.btnStopService.isEnabled = false
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

    private fun initControllers() {
        logHelper = LogHelper(this, binding.tvLog, binding.scrollViewLog)
        serviceController = ServiceController(this, settingsManager)
        uiUpdater = UIUpdater(this, settingsManager)
        navigationHandler = NavigationHandler(this, this::showMediaTypeDialog, settingsManager, uiUpdater)
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

        setupButtonListeners()
        setupAdapterListeners()

        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
            }
        }
    }

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
                    saveFolders()
                    logHelper.log("Папка '$removedFolderName' удалена.")
                    serviceController.stopService()
                    binding.btnStartService.isEnabled = true
                    binding.btnStopService.isEnabled = false
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
    }

    private fun showResetCacheConfirmationDialog(folder: Folder) {
        AlertDialog.Builder(this)
            .setTitle("Сброс кэша")
            .setMessage("Вы уверены, что хотите сбросить кэш отправленных файлов для папки \"${folder.name}\"? Приложение забудет, какие файлы уже были отправлены, и попытается отправить их все заново.")
            .setPositiveButton("Да, сбросить") { _, _ ->
                resetSentFilesCacheForFolder(folder)
                logHelper.log("Кэш для папки \"${folder.name}\" очищен.")
                serviceController.stopService()
                binding.btnStartService.isEnabled = true
                binding.btnStopService.isEnabled = false
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun resetSentFilesCacheForFolder(folder: Folder) {
        val sentFilesPrefs = getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        val editor = sentFilesPrefs.edit()

        val folderPathUri = folder.path

        sentFilesPrefs.all.keys.forEach { fileUriString ->
            if (!fileUriString.startsWith(folderPathUri)) {
                return@forEach
            }

            val docFile = DocumentFile.fromSingleUri(this, Uri.parse(fileUriString))
            if (docFile != null && docFile.exists()) {
                if (isValidMediaForReset(docFile, folder.mediaType)) {
                    editor.remove(fileUriString)
                    Log.d(TAG, "Removing from cache (matches media type): $fileUriString")
                }
            }
        }

        editor.apply()
        Log.d(TAG, "Cache reset for folder path: $folderPathUri with media type: ${folder.mediaType}")
    }

    private fun isValidMediaForReset(file: DocumentFile, mediaType: String): Boolean {
        val fileName = file.name?.lowercase() ?: return false
        val isFilePhoto = listOf(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp").any { fileName.endsWith(it) }
        val isFileVideo = listOf(".mp4", ".mkv", ".webm", ".avi", ".mov", ".3gp", ".flv").any { fileName.endsWith(it) }

        val photoTypeString = getString(R.string.only_photo).lowercase() // "фото"
        val videoTypeString = getString(R.string.only_video).lowercase() // "видео"
        val allTypeString = getString(R.string.all_media).lowercase()   // "все"

        return when (mediaType.lowercase()) {
            photoTypeString -> isFilePhoto
            videoTypeString -> isFileVideo
            allTypeString -> isFilePhoto || isFileVideo
            else -> false
        }
    }

    private fun loadData() {
        logHelper.clearSavedLog()
        loadFolders()
        uiUpdater.updateSettingsDisplay()
        if (settingsManager.botToken.isNullOrEmpty() || settingsManager.chatId.isNullOrEmpty()) {
            logHelper.log(getString(R.string.configure_bot_token_chat_id))
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(logReceiver, IntentFilter("com.example.photouploaderapp.UPLOAD_LOG"))
        //checkPersistedPermissions()
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
        AddFolderDialog(settingsManager, object : AddFolderDialog.AddFolderListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onFolderAdded(folder: Folder) {
                folders.add(folder)
                folderAdapter.notifyDataSetChanged()
                saveFolders()
                logHelper.log("Добавлена новая папка: '${folder.name}'")
            }
        }).show(supportFragmentManager, "AddFolderDialog")
    }

    private fun showEditFolderDialog(position: Int) {
        val folder = folders[position]
        EditFolderDialog(settingsManager, folder, object : EditFolderDialog.EditFolderListener {
            override fun onFolderEdited(editedFolder: Folder) {
                if (folder != editedFolder) {
                    serviceController.stopService()
                    logHelper.log(getString(R.string.folder_changes_detected))
                }
                folders[position] = editedFolder
                folderAdapter.notifyItemChanged(position)
                saveFolders()
            }
        }).show(supportFragmentManager, "EditFolderDialog")
    }

    private fun setupButtonListeners() {
        binding.btnStartService.setOnClickListener {
            val foldersToSync = folders.filter { it.isSyncing }
            if (foldersToSync.isNotEmpty()) {
                serviceController.startService(foldersToSync)
                binding.btnStartService.isEnabled = false
                binding.btnStopService.isEnabled = true
            } else {
                logHelper.log("Нет папок, отмеченных для синхронизации.")
            }
        }

        binding.btnStopService.setOnClickListener {
            serviceController.stopService()
            binding.btnStartService.isEnabled = true
            binding.btnStopService.isEnabled = false
        }
    }

    fun toggleTheme() {
        showToast("Тема теперь переключается в настройках системы")
    }

    internal fun showMediaTypeDialog() {
        showToast("Логика выбора типа медиа")
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveFolders() {
        val json = Gson().toJson(folders)
        sharedPreferences.edit { putString("folders", json) }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun loadFolders() {
        val json = sharedPreferences.getString("folders", null)
        if (json != null) {
            val type = object : TypeToken<MutableList<Folder>>() {}.type
            try {
                val savedFolders: MutableList<Folder> = Gson().fromJson(json, type)
                folders.clear()
                folders.addAll(savedFolders)
                folderAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load folders", e)
            }
        }
    }

    private fun checkPersistedPermissions() {
        val persistedUris = contentResolver.persistedUriPermissions
        val persistedUriStrings = persistedUris.map { it.uri.toString() }

        var permissionsLost = false
        folders.forEach { folder ->
            if (folder.path.isNotEmpty() && !persistedUriStrings.contains(folder.path)) {
                logHelper.log("ВНИМАНИЕ: Потерян доступ к папке '${folder.name}'. Пожалуйста, выберите ее заново в настройках папки.")
                permissionsLost = true
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
