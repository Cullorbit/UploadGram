package com.example.photouploaderapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.GravityCompat
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs

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
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var gestureDetector: GestureDetector

    lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Разрешение на уведомления необходимо для работы в фоне", Toast.LENGTH_LONG).show()
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logHelper.loadSavedLog()
        }
    }

    private val serviceStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isRunning = intent?.getBooleanExtra("is_running", false) ?: false
            updateServiceState(isRunning)
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
        initGestureDetector()

        updateServiceState(settingsManager.isServiceRunning)

        checkNotificationPermission()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.drawerLayout.post {
                val exclusionRects = mutableListOf<Rect>()
                val rect = Rect(0, 0, 200, binding.drawerLayout.height)
                exclusionRects.add(rect)
                binding.drawerLayout.systemGestureExclusionRects = exclusionRects
            }
        }
    }

    private fun initGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                if (abs(diffX) > abs(diffY) && diffX > 100 && abs(velocityX) > 100) {
                    if (!binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        drawerToggle = ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(drawerToggle)
        binding.drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        drawerToggle.syncState()
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
            } else {
                if (settingsManager.botToken.isNullOrEmpty() || settingsManager.chatId.isNullOrEmpty()) {
                    LogHelper.writeLog(this, getString(R.string.configure_bot_token_chat_id))
                    return@setOnClickListener
                }
                if (folders.none { it.isSyncing }) {
                    LogHelper.writeLog(this, getString(R.string.no_folders_for_sync))
                    return@setOnClickListener
                }
                serviceController.startService(folders)
            }
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
                    LogHelper.writeLog(this@MainActivity, getString(R.string.folder_deleted, removedFolderName))
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
                LogHelper.writeLog(this@MainActivity, getString(R.string.cache_cleared_for_folder, folder.name))
                stopServiceIfNeeded(getString(R.string.service_stopped_due_to_cache_reset))
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun resetSentFilesCacheForFolder(folder: Folder) {
        val sentFilesPrefs = getSharedPreferences("SentFiles", Context.MODE_PRIVATE)
        val allEntries = sentFilesPrefs.all
        val folderUriPrefix = folder.path
        sentFilesPrefs.edit().apply {
            allEntries.keys.forEach { key ->
                if (key.startsWith(folderUriPrefix)) {
                    remove(key)
                }
            }
            apply()
        }
    }

    private fun showAddFolderDialog() {
        val dialog = AddFolderDialog(settingsManager)
        dialog.show(supportFragmentManager, "AddFolderDialog")
    }

    private fun showEditFolderDialog(position: Int) {
        val folder = folders[position]
        val dialog = EditFolderDialog(settingsManager, folder)
        dialog.show(supportFragmentManager, "EditFolderDialog")
    }

    fun showSyncIntervalDialog() {
        val dialog = SyncIntervalDialog(settingsManager)
        dialog.show(supportFragmentManager, "SyncIntervalDialog")
    }

    fun showCacheLimitDialog() {
        val limits = arrayOf(
            getString(R.string.cache_unlimited),
            getString(R.string.cache_size_gb, 2),
            getString(R.string.cache_size_gb, 5),
            getString(R.string.cache_size_gb, 16)
        )
        val limitValues = arrayOf(0L, 2L, 5L, 16L).map { it * 1024 * 1024 * 1024 }
        
        val currentLimit = settingsManager.cacheLimit
        val checkedItem = limitValues.indexOf(currentLimit).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.cache_limit))
            .setSingleChoiceItems(limits, checkedItem) { dialog, which ->
                settingsManager.cacheLimit = limitValues[which]
                uiUpdater.updateSettingsDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    fun stopServiceIfNeeded(message: String? = null) {
        if (isServiceRunning) {
            serviceController.stopService()
            message?.let { LogHelper.writeLog(this, it) }
        }
    }

    private fun updateServiceState(isRunning: Boolean) {
        isServiceRunning = isRunning
        binding.btnToggleService.text = if (isRunning) getString(R.string.stop_service) else getString(R.string.start_service)
        uiUpdater.updateSettingsDisplay()
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
        logHelper.loadSavedLog()
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
        uiUpdater.updateSettingsDisplay()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStateReceiver)
    }

    override fun onSupportNavigateUp(): Boolean {
        binding.drawerLayout.openDrawer(GravityCompat.START)
        return true
    }
}
