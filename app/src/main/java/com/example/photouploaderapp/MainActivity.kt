package com.example.photouploaderapp

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.example.photouploaderapp.configs.*
import com.example.photouploaderapp.telegrambot.TelegramResponse
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    lateinit var btnStartService: Button
    lateinit var btnStopService: Button
    private lateinit var btnClearLog: Button
    private lateinit var tvLog: TextView
    private lateinit var scrollViewLog: ScrollView
    private val folders = mutableListOf<Folder>()
    private val folderAdapter = FolderAdapter(folders)
    private lateinit var logHelper: LogHelper
    private lateinit var recyclerViewFolders: RecyclerView
    private lateinit var fabAddFolder: FloatingActionButton

    // Компоненты управления
    private lateinit var settingsManager: SettingsManager
    private lateinit var serviceController: ServiceController
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var uiUpdater: UIUpdater

    // ActivityResultLauncher для выбора папки
    lateinit var folderPickerLauncher: ActivityResultLauncher<Uri?>

    // BroadcastReceiver для получения логов от UploadService
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra("log_message") ?: return
            val folderName = intent.getStringExtra("folder_name") ?: "Папка"
            tvLog.append("\n\n[$folderName] $message")
            scrollViewLog.post { scrollViewLog.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private val sharedPreferences by lazy {
        getSharedPreferences("Folders", Context.MODE_PRIVATE)
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        settingsManager = SettingsManager(this)

        val isDarkTheme = settingsManager.isDarkTheme
        setTheme(if (isDarkTheme) R.style.AppTheme_Dark else R.style.AppTheme_Light)
        AppCompatDelegate.setDefaultNightMode(
            if (settingsManager.isDarkTheme)
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        folderAdapter.setOnItemClickListener(object : FolderAdapter.OnItemClickListener {
            override fun onItemClick(position: Int) {
                showEditFolderDialog(position)
            }
        })

        folderAdapter.setOnDeleteClickListener(object : FolderAdapter.OnDeleteClickListener {
            override fun onDeleteClick(position: Int) {
                if (position >= 0 && position < folders.size) {
                    folders.removeAt(position)
                    folderAdapter.notifyItemRemoved(position)
                    saveFolders()
                } else {
                    Log.e("MainActivity", "Invalid position: $position, list size: ${folders.size}")
                }
            }
        })

        recyclerViewFolders = findViewById(R.id.recyclerViewFolders)
        recyclerViewFolders.layoutManager = LinearLayoutManager(this)
        recyclerViewFolders.adapter = folderAdapter

        fabAddFolder = findViewById(R.id.fabAddFolder)
        fabAddFolder.setOnClickListener {
            showAddFolderDialog()
        }

        settingsManager = SettingsManager(this)
        (application as? MyApplication)?.folderAdapter = folderAdapter

        initViews()

        // Настройка Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        folderAdapter.setOnCancelClickListener(object : FolderAdapter.OnCancelClickListener {
            override fun onCancelClick(position: Int) {
                folders[position].isSyncing = false
                folderAdapter.notifyItemChanged(position)
                saveFolders()
            }
        })

        logHelper = LogHelper(this)
        logHelper.getLog()

        btnClearLog.setOnClickListener {
            logHelper.clearLog()
        }

        // Инициализация компонентов управления
        serviceController = ServiceController(this, settingsManager)
        uiUpdater = UIUpdater(this, settingsManager)
        navigationHandler = NavigationHandler(this, this::showMediaTypeDialog, settingsManager, uiUpdater)

        // Инициализация ActivityResultLauncher для выбора папки
        folderPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                settingsManager.syncFolder = it.toString()
                uiUpdater.updateSettingsDisplay()
            }
        }
        loadSettings()
        loadFolders()
        folderAdapter.notifyDataSetChanged()
        
        setupButtonListeners()
    }

    internal fun showSyncIntervalDialog() {
        val dialog = SyncIntervalDialog(settingsManager)
        dialog.show(supportFragmentManager, "SyncIntervalDialog")
    }

    private fun showAddFolderDialog() {
        val dialog = AddFolderDialog(settingsManager, object : AddFolderDialog.AddFolderListener {
            override fun onFolderAdded(folder: Folder) {
                folders.add(folder)
                folderAdapter.notifyDataSetChanged()
                saveFolders()
            }
        })
        dialog.show(supportFragmentManager, "AddFolderDialog")
    }

    private fun showEditFolderDialog(position: Int) {
        val folder = folders[position]
        val originalFolder = folder.copy()

        val dialog = EditFolderDialog(settingsManager, folder, object : EditFolderDialog.EditFolderListener {
            override fun onFolderEdited(editedFolder: Folder) {
                if (folder != editedFolder) {
                    serviceController.stopService()
                    uiUpdater.updateServiceButtons(serviceController.isServiceActive())
                    showToast("Изменения в папке обнаружены. Сервис остановлен.")
                }
                editedFolder.botToken = settingsManager.botToken ?: ""
                editedFolder.chatId = settingsManager.chatId ?: ""
                folders[position] = editedFolder
                folderAdapter.notifyItemChanged(position)
                saveFolders()
            }
        })
        dialog.show(supportFragmentManager, "EditFolderDialog")
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationView = findViewById(R.id.navigationView)
        btnStartService = findViewById(R.id.btnStartService)
        btnStopService = findViewById(R.id.btnStopService)
        btnClearLog = findViewById(R.id.btnClearLog)
        tvLog = findViewById(R.id.tvLog)
        scrollViewLog = findViewById(R.id.scrollViewLog)

        navigationView.setNavigationItemSelectedListener { menuItem ->
            navigationHandler.handleNavigationItemSelected(menuItem)
            drawerLayout.closeDrawer(GravityCompat.END)
            true
        }
    }

    private fun loadSettings() {
        settingsManager.syncFolder?.let { uriStr ->
            try {
                val uri = Uri.parse(uriStr)
                if (!DocumentFile.isDocumentUri(this, uri)) {
                    settingsManager.syncFolder = null
                    return@let
                }
                // Логируем сохранённые разрешения
                contentResolver.persistedUriPermissions.forEach {
                    Log.d(TAG, "Persisted URI: ${it.uri}, writePermission: ${it.isWritePermission}")
                }
                // Проверяем наличие разрешения с использованием toString()
                val hasPermission = contentResolver.persistedUriPermissions.any {
                    it.uri.toString() == uri.toString() && it.isWritePermission
                }
                if (!hasPermission) {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    Log.d(TAG, "Persisted permissions restored")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring folder permissions", e)
                settingsManager.syncFolder = null
            }
        }
        uiUpdater.updateSettingsDisplay()
        updateServiceButtons()
        if (settingsManager.botToken.isNullOrEmpty() || settingsManager.chatId.isNullOrEmpty()) {
            showToast("Сначала настройте Bot Token и Chat ID в глобальных настройках!")
        }
    }

    private fun setupButtonListeners() {
        btnStartService.setOnClickListener {
            if (serviceController.isServiceActive()) {
                showToast("Сервис уже запущен")
            } else {
                val networkUtils = NetworkUtils(this)
                val syncOption = settingsManager.syncOption

                when (syncOption) {
                    "wifi_only" -> {
                        if (!networkUtils.isWifiConnected()) {
                            showToast("Синхронизация доступна только по Wi-Fi.")
                            return@setOnClickListener
                        }
                    }
                    else -> {
                        if (!networkUtils.isConnected()) {
                            showToast("Отсутствует интернет-соединение.")
                            return@setOnClickListener
                        }
                    }
                }

                for (folder in folders) {
                    if (folder.isSyncing) {
                        if (folder.isTopicEnabled && folder.topic.isNotEmpty()) {
                            checkForumTopic(folder) { topicExists ->
                                if (topicExists) {
                                    serviceController.startService(listOf(folder))
                                    uiUpdater.updateServiceButtons(serviceController.isServiceActive())
                                } else {
                                    showToast("Тема №${folder.topic} не существует. Проверьте номер темы.")
                                }
                            }
                        } else {
                            serviceController.startService(listOf(folder))
                            uiUpdater.updateServiceButtons(serviceController.isServiceActive())
                        }
                    }
                }
            }
        }

        btnStopService.setOnClickListener {
            serviceController.stopService()
            uiUpdater.updateServiceButtons(serviceController.isServiceActive())
        }

        btnClearLog.setOnClickListener {
            tvLog.text = "Лог:"
            logHelper.clearLog()
        }
    }

    private fun checkForumTopic(folder: Folder, callback: (Boolean) -> Unit) {
        val url = "https://api.telegram.org/bot${settingsManager.botToken}/getForumTopic?chat_id=${settingsManager.chatId}&message_thread_id=${folder.topic}"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Ошибка при проверке темы: ${e.message}")
                callback(false)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseString = response.body?.string() ?: "No response body"
                    Log.d(TAG, "Проверка темы: $responseString")
                    try {
                        val telegramResponse = gson.fromJson(responseString, TelegramResponse::class.java)
                        if (telegramResponse.ok) {
                            Log.d(TAG, "Тема существует")
                            callback(true)
                        } else {
                            Log.e(TAG, "Тема не существует: ${telegramResponse.description}")
                            callback(false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Ошибка при парсинге ответа Telegram: ${e.message}")
                        callback(false)
                    }
                } else {
                    Log.e(TAG, "Ошибка при проверке темы. Код ответа: ${response.code}, Сообщение: ${response.message}")
                    val responseBody = response.body?.string() ?: "No response body"
                    Log.e(TAG, "Ответ сервера: $responseBody")
                    callback(false)
                }
                response.close()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(logReceiver, IntentFilter("com.example.photouploaderapp.UPLOAD_LOG"))
        updateServiceButtons()
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(logReceiver)
        super.onPause()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val themeItem = menu?.findItem(R.id.menu_toggle_theme)
        themeItem?.isChecked = settingsManager.isDarkTheme
        Log.d("MainActivity", "Menu created. Theme item checked: ${themeItem?.isChecked}")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                drawerLayout.openDrawer(GravityCompat.END)
                true
            }
            R.id.menu_toggle_theme -> {
                toggleTheme()
                true
            }
            else -> navigationHandler.handleNavigationItemSelected(item) || super.onOptionsItemSelected(item)
        }
    }

    fun toggleTheme() {
        val newTheme = !settingsManager.isDarkTheme
        settingsManager.isDarkTheme = newTheme

        AppCompatDelegate.setDefaultNightMode(
            if (newTheme) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        delegate.applyDayNight()
        uiUpdater.updateSettingsDisplay()
        recreate()
    }

    private fun updateServiceButtons() {
        uiUpdater.updateServiceButtons(serviceController.isServiceActive())
    }

    fun showMediaTypeDialog() {
        val options = arrayOf("Фото и видео", "Только фото", "Только видео")
        val current = settingsManager.mediaType
        val checkedItem = when (current) {
            "photo" -> 1
            "video" -> 2
            else -> 0
        }
        var selectedIndex = checkedItem

        AlertDialog.Builder(this)
            .setTitle("Задать тип медиа")
            .setSingleChoiceItems(options, checkedItem) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("OK") { dialog, _ ->
                val newValue = when (selectedIndex) {
                    1 -> "photo"
                    2 -> "video"
                    else -> "all"
                }
                settingsManager.mediaType = newValue
                showToast("Тип медиа сохранён: ${options[selectedIndex]}")
                uiUpdater.updateSettingsDisplay()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }

    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun saveFolders() {
        val gson = Gson()
        val foldersJson = gson.toJson(folders)
        sharedPreferences.edit { putString("folders", foldersJson) }
    }

    private fun loadFolders() {
        val gson = Gson()
        val foldersJson = sharedPreferences.getString("folders", null)
        if (foldersJson != null) {
            val type = object : TypeToken<MutableList<Folder>>() {}.type
            folders.addAll(gson.fromJson(foldersJson, type))
            folders.forEach { folder ->
                if (folder.botToken.isNullOrEmpty()) {
                    folder.botToken = settingsManager.botToken ?: ""
                }
                if (folder.chatId.isNullOrEmpty()) {
                    folder.chatId = settingsManager.chatId ?: ""
                }
            }
            Log.d("MainActivity", "Загружено папок: ${folders.size}")
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private val client = OkHttpClient()
        private val gson = Gson()
    }
}
