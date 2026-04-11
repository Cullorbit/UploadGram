package com.example.photouploaderapp.configs

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.photouploaderapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BotSetupHandler(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val settingsManager: SettingsManager,
    private val onComplete: (String, String) -> Unit
) {

    fun startBotSetupFlow() {
        val verificationCode = "auth" + (100000..999999).random().toString()
        val botToken = settingsManager.botToken.ifBlank { SettingsManager.INTERNAL_AUTH_BOT_TOKEN }
        
        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val botUsername = getBotUsername(botToken)
            withContext(Dispatchers.Main) {
                val url = "https://t.me/$botUsername?startgroup=$verificationCode&admin=post_messages"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                context.startActivity(intent)
                connectBotToChat(verificationCode)
            }
        }
    }

    private suspend fun getBotUsername(botToken: String): String {
        val client = OkHttpClient()
        val url = "${settingsManager.proxyUrl}/bot$botToken/getMe"
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    json.optJSONObject("result")?.optString("username", "Bot") ?: "Bot"
                } else "Bot"
            }
        } catch (e: Exception) {
            "Bot"
        }
    }

    private fun connectBotToChat(verificationCode: String) {
        val botToken = settingsManager.botToken.ifBlank { SettingsManager.INTERNAL_AUTH_BOT_TOKEN }
        
        val progressDialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.bot_authorization))
            .setMessage(context.getString(R.string.waiting_for_chat_id))
            .setCancelable(false)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()

        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            
            var currentOffset = -1L 
            val processedUpdates = mutableSetOf<Long>()
            val startTime = System.currentTimeMillis()
            val timeout = 300000L 
            
            var potentialChatId: String? = null
            var potentialChatTitle: String? = null
            var lastAttemptTime = 0L

            try {
                while (System.currentTimeMillis() - startTime < timeout) {
                    val url = "${settingsManager.proxyUrl}/bot$botToken/getUpdates" +
                            "?offset=$currentOffset&limit=50&timeout=20"
                    
                    try {
                        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: "{}"
                                val json = JSONObject(body)
                                val result = json.optJSONArray("result")
                                if (result != null) {
                                    for (i in 0 until result.length()) {
                                        val update = result.getJSONObject(i)
                                        val updateId = update.getLong("update_id")
                                        
                                        if (processedUpdates.add(updateId)) {
                                            currentOffset = updateId + 1
                                            
                                            val chatObj = update.optJSONObject("message")?.optJSONObject("chat")
                                                ?: update.optJSONObject("channel_post")?.optJSONObject("chat")
                                                ?: update.optJSONObject("my_chat_member")?.optJSONObject("chat")
                                                ?: update.optJSONObject("chat_member")?.optJSONObject("chat")

                                            if (chatObj != null) {
                                                val id = chatObj.optLong("id", 0L)
                                                if (id != 0L) {
                                                    val foundChatId = id.toString()
                                                    val foundChatTitle = chatObj.optString("title", chatObj.optString("username", context.getString(R.string.chat)))
                                                    
                                                    withContext(Dispatchers.Main) {
                                                        progressDialog.setMessage(context.getString(R.string.bot_in_chat, foundChatTitle))
                                                    }

                                                    if (potentialChatId != foundChatId) {
                                                        potentialChatId = foundChatId
                                                        potentialChatTitle = foundChatTitle
                                                        
                                                        val autoMsg = context.getString(R.string.app_auth_message, verificationCode)
                                                        val sendUrl = "${settingsManager.proxyUrl}/bot$botToken/sendMessage?chat_id=$potentialChatId&text=${Uri.encode(autoMsg)}"
                                                        
                                                        try {
                                                            client.newCall(Request.Builder().url(sendUrl).build()).execute().use { resp ->
                                                                if (resp.isSuccessful) {
                                                                    finalizeConnection(potentialChatId!!, potentialChatTitle ?: "Chat", client, progressDialog, botToken)
                                                                    return@launch
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e("BotSetupHandler", "Failed to send auto-message", e)
                                                        }
                                                    }
                                                }
                                            }

                                            if (checkUpdateForCode(update, verificationCode)) {
                                                if (potentialChatId != null) {
                                                    finalizeConnection(potentialChatId!!, potentialChatTitle ?: "", client, progressDialog, botToken)
                                                    return@launch
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (potentialChatId != null) {
                        val now = System.currentTimeMillis()
                        if (now - lastAttemptTime > 5000) { 
                            lastAttemptTime = now
                            val autoMsg = context.getString(R.string.app_auth_message, verificationCode)
                            val sendUrl = "${settingsManager.proxyUrl}/bot$botToken/sendMessage?chat_id=$potentialChatId&text=${Uri.encode(autoMsg)}"
                            
                            try {
                                client.newCall(Request.Builder().url(sendUrl).build()).execute().use { resp ->
                                    if (resp.isSuccessful) {
                                        finalizeConnection(potentialChatId!!, potentialChatTitle ?: "Chat", client, progressDialog, botToken)
                                        return@launch
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    delay(1000)
                }
                
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toast.makeText(context, context.getString(R.string.error_bot_timeout), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                }
            }
        }
    }

    private suspend fun finalizeConnection(
        sChatId: String,
        chatTitle: String,
        client: OkHttpClient,
        progressDialog: AlertDialog,
        botToken: String
    ) {
        withContext(Dispatchers.Main) {
            onComplete(sChatId, chatTitle)
            progressDialog.dismiss()
            Toast.makeText(context, context.getString(R.string.bot_connected_success), Toast.LENGTH_SHORT).show()
        }

        val welcomeMsg = context.getString(R.string.bot_connected_success) + "\n" +
                context.getString(R.string.folder) + ": $chatTitle\nID: $sChatId"
        val sendUrl = "${settingsManager.proxyUrl}/bot$botToken/sendMessage?chat_id=$sChatId&text=${Uri.encode(welcomeMsg)}"
        
        try {
            client.newCall(Request.Builder().url(sendUrl).build()).execute().use {}
        } catch (e: Exception) {}
    }

    private fun checkUpdateForCode(update: JSONObject, code: String): Boolean {
        val message = update.optJSONObject("message") ?: update.optJSONObject("channel_post")
        val text = message?.optString("text") ?: ""
        return text.contains(code)
    }
}
