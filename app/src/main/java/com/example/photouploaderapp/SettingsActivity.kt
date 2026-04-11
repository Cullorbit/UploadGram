package com.example.photouploaderapp

import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.photouploaderapp.configs.BotSetupHandler
import com.example.photouploaderapp.configs.SettingsManager
import com.example.photouploaderapp.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        private lateinit var settingsManager: SettingsManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "Settings"
            
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            settingsManager = SettingsManager(requireContext())

            setupProxyPreference()
            setupBotTokenPreference()
            updateBotConnectionSummary()
            setupAppInfoPreference()

            findPreference<Preference>("menu_manual")?.setOnPreferenceClickListener {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                intent.data = android.net.Uri.parse("https://github.com/Cullorbit/UploadGram/blob/master/USAGE.md")
                startActivity(intent)
                true
            }

            findPreference<Preference>("menu_reset_settings")?.setOnPreferenceClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reset_app_settings)
                    .setMessage(R.string.reset_settings_confirmation)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        settingsManager.clearSettings()
                        requireActivity().finishAffinity()
                        startActivity(android.content.Intent(requireContext(), MainActivity::class.java))
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
                true
            }
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            super.onPause()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        }

        private fun setupProxyPreference() {
            val proxyPref = findPreference<Preference>("proxy_url")
            
            val updateSummary = {
                val currentInPrefs = settingsManager.getRawProxyUrl()
                proxyPref?.summary = if (currentInPrefs.isNullOrEmpty()) {
                    getString(R.string.default_button)
                } else {
                    currentInPrefs
                }
            }
            
            updateSummary()

            proxyPref?.setOnPreferenceClickListener {
                showProxyDialog(updateSummary)
                true
            }
        }

        private fun setupAppInfoPreference() {
            val appInfoPref = findPreference<Preference>("menu_app_info")
            try {
                val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
                appInfoPref?.title = getString(R.string.app_name_version, packageInfo.versionName)
            } catch (e: Exception) {
                appInfoPref?.title = getString(R.string.app_name)
            }

            appInfoPref?.setOnPreferenceClickListener {
                checkForUpdates(appInfoPref)
                true
            }
        }

        private fun checkForUpdates(preference: Preference?) {
            val context = requireContext()
            val currentVersion = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Exception) {
                "0.0.0"
            }

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

                            withContext(Dispatchers.Main) {
                                if (isNewerVersion(currentVersion, cleanLatestTag)) {
                                    preference?.summary = getString(R.string.update_available, latestTag)
                                    MaterialAlertDialogBuilder(context)
                                        .setTitle(R.string.update_available_title)
                                        .setMessage(getString(R.string.update_available, latestTag))
                                        .setPositiveButton(R.string.yes) { _, _ ->
                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/Cullorbit/UploadGram/releases"))
                                            startActivity(intent)
                                        }
                                        .setNegativeButton(R.string.no, null)
                                        .show()
                                } else {
                                    Toast.makeText(context, R.string.latest_version_installed, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.network_error, Toast.LENGTH_SHORT).show()
                    }
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

        private fun setupBotTokenPreference() {
            val botTokenPref = findPreference<Preference>("bot_token")
            
            val updateSummary = {
                val currentInPrefs = settingsManager.getRawBotToken()
                botTokenPref?.summary = if (currentInPrefs.isNullOrEmpty()) {
                    getString(R.string.default_button)
                } else {
                    currentInPrefs
                }
            }
            
            updateSummary()

            botTokenPref?.setOnPreferenceClickListener {
                showBotTokenDialog(updateSummary)
                true
            }
            
            findPreference<Preference>("menu_add_bot")?.setOnPreferenceClickListener {
                BotSetupHandler(requireContext(), this, settingsManager) { chatId, title ->
                    settingsManager.chatId = chatId
                    settingsManager.chatTitle = title
                    updateBotConnectionSummary()
                }.startBotSetupFlow()
                true
            }

            findPreference<ListPreference>("theme_mode")?.setOnPreferenceChangeListener { _, newValue ->
                val themeInt = (newValue as String).toInt()
                settingsManager.themeMode = themeInt
                AppCompatDelegate.setDefaultNightMode(themeInt)
                true
            }
        }

        private fun showBotTokenDialog(onChanged: () -> Unit) {
            val currentRaw = settingsManager.getRawBotToken() ?: ""
            val editText = EditText(requireContext()).apply {
                setText(currentRaw)
                setSelection(text.length)
                hint = getString(R.string.enter_bot_token)
            }

            val container = FrameLayout(requireContext())
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            val margin = (20 * resources.displayMetrics.density).toInt()
            params.setMargins(margin, margin / 2, margin, 0)
            editText.layoutParams = params
            container.addView(editText)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.use_own_bot_token)
                .setMessage(R.string.bot_token_description)
                .setView(container)
                .setNeutralButton(R.string.default_button) { _, _ ->
                    settingsManager.botToken = ""
                    onChanged()
                    Toast.makeText(requireContext(), R.string.bot_token_default_toast, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val newToken = editText.text.toString().trim()
                    if (newToken.isEmpty()) {
                        settingsManager.botToken = ""
                        onChanged()
                    } else if (newToken.contains(":") && newToken.length > 10) {
                        settingsManager.botToken = newToken
                        onChanged()
                    } else {
                        Toast.makeText(requireContext(), R.string.invalid_bot_token_format, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        private fun showProxyDialog(onChanged: () -> Unit) {
            val currentRaw = settingsManager.getRawProxyUrl() ?: ""
            val editText = EditText(requireContext()).apply {
                setText(currentRaw)
                setSelection(text.length)
                hint = getString(R.string.enter_proxy_url)
            }

            val container = FrameLayout(requireContext())
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            val margin = (20 * resources.displayMetrics.density).toInt()
            params.setMargins(margin, margin / 2, margin, 0)
            editText.layoutParams = params
            container.addView(editText)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.set_proxy_url)
                .setMessage(R.string.proxy_url_description)
                .setView(container)
                .setNeutralButton(R.string.default_button) { _, _ ->
                    settingsManager.proxyUrl = ""
                    onChanged()
                    Toast.makeText(requireContext(), R.string.proxy_url_default_toast, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save) { _, _ ->
                    val newUrl = editText.text.toString().trim()
                    if (newUrl.isEmpty()) {
                        settingsManager.proxyUrl = ""
                        onChanged()
                    } else if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                        settingsManager.proxyUrl = newUrl
                        onChanged()
                    } else {
                        Toast.makeText(requireContext(), R.string.invalid_url_format, Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }

        private fun updateBotConnectionSummary() {
            val botPreference = findPreference<Preference>("menu_add_bot")
            val chatId = settingsManager.chatId
            val chatTitle = settingsManager.chatTitle
            
            if (chatId != null) {
                botPreference?.summary = getString(R.string.bot_connected, chatTitle ?: chatId)
            } else {
                botPreference?.summary = getString(R.string.bot_not_connected)
            }
        }
    }
}
