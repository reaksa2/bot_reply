package com.example.manualreply

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object AccountManager {
    private const val PREFS_NAME = "manual_reply_accounts"
    private const val KEY_ACCOUNTS = "accounts_json"
    private const val KEY_ACTIVE_ID = "active_account_id"

    fun getAccounts(context: Context): List<Account> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ACCOUNTS, null) ?: return emptyList()
        val array = JSONArray(json)
        val result = mutableListOf<Account>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                Account(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    botId = obj.getString("botId"),
                    botToken = obj.getString("botToken")
                )
            )
        }
        return result
    }

    private fun saveAccounts(context: Context, accounts: List<Account>) {
        val array = JSONArray()
        accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("botId", acc.botId)
            obj.put("botToken", acc.botToken)
            array.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    // Called AFTER the app has already successfully registered the bot with
    // the Worker — this just remembers it locally for the account switcher.
    fun addAccount(context: Context, name: String, botId: String, botToken: String): Account {
        val newAccount = Account(
            id = UUID.randomUUID().toString(),
            name = name,
            botId = botId,
            botToken = botToken
        )
        val accounts = getAccounts(context).toMutableList()
        accounts.add(newAccount)
        saveAccounts(context, accounts)

        if (getActiveAccountId(context) == null) {
            setActiveAccount(context, newAccount.id)
        }
        return newAccount
    }

    fun deleteAccount(context: Context, id: String) {
        val accounts = getAccounts(context).filter { it.id != id }
        saveAccounts(context, accounts)

        if (getActiveAccountId(context) == id) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (accounts.isNotEmpty()) {
                prefs.edit().putString(KEY_ACTIVE_ID, accounts.first().id).apply()
            } else {
                prefs.edit().remove(KEY_ACTIVE_ID).apply()
            }
        }
    }

    fun setActiveAccount(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun getActiveAccountId(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_ID, null)
    }

    fun getActiveAccount(context: Context): Account? {
        val activeId = getActiveAccountId(context) ?: return null
        return getAccounts(context).find { it.id == activeId }
    }
}
