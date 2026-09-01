package com.example.manualreply

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AccountsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: View
    private lateinit var adapter: AccountAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_accounts)

        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)

        findViewById<ImageButton>(R.id.backButton).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.addButton).setOnClickListener { showAddDialog() }

        adapter = AccountAdapter(
            emptyList(),
            null,
            onSelect = { account ->
                AccountManager.setActiveAccount(this, account.id)
                refreshList()
                Toast.makeText(this, "Switched to ${account.name}", Toast.LENGTH_SHORT).show()
            },
            onDelete = { account -> confirmDelete(account) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val accounts = AccountManager.getAccounts(this)
        val activeId = AccountManager.getActiveAccountId(this)
        adapter.updateItems(accounts, activeId)
        emptyText.visibility = if (accounts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        val padding = (16 * resources.displayMetrics.density).toInt()
        layout.setPadding(padding, padding, padding, padding)

        val nameInput = EditText(this).apply { hint = "Bot name (e.g. My Shop Bot)" }
        val tokenInput = EditText(this).apply { hint = "Bot token from @BotFather" }

        layout.addView(nameInput)
        layout.addView(tokenInput)

        AlertDialog.Builder(this)
            .setTitle("Add a bot")
            .setView(layout)
            .setPositiveButton("Add", null) // set below so we can control dismiss timing
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = nameInput.text.toString().trim()
                        val token = tokenInput.text.toString().trim()

                        if (name.isEmpty() || token.isEmpty()) {
                            Toast.makeText(this@AccountsActivity, "Both fields are required", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        if (!token.contains(":")) {
                            Toast.makeText(this@AccountsActivity, "Bot token looks invalid", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        registerAndAdd(name, token, this)
                    }
                }
            }
            .show()
    }

    private fun registerAndAdd(name: String, token: String, dialog: AlertDialog) {
        Toast.makeText(this, "Connecting your bot...", Toast.LENGTH_SHORT).show()
        Thread {
            val result = ApiClient.registerBot(token)
            runOnUiThread {
                if (result.success && result.botId != null) {
                    AccountManager.addAccount(this, name, result.botId, token)
                    dialog.dismiss()
                    refreshList()
                    Toast.makeText(this, "Bot connected!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed: ${result.error}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun confirmDelete(account: Account) {
        AlertDialog.Builder(this)
            .setTitle("Remove bot")
            .setMessage("Remove \"${account.name}\" from this app? This won't delete the bot itself.")
            .setPositiveButton("Remove") { _, _ ->
                AccountManager.deleteAccount(this, account.id)
                refreshList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
