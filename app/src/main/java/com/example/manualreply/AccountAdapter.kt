package com.example.manualreply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AccountAdapter(
    private var items: List<Account>,
    private var activeId: String?,
    private val onSelect: (Account) -> Unit,
    private val onDelete: (Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val check: TextView = view.findViewById(R.id.checkText)
        val name: TextView = view.findViewById(R.id.nameText)
        val delete: ImageButton = view.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.check.visibility = if (item.id == activeId) View.VISIBLE else View.INVISIBLE
        holder.itemView.setOnClickListener { onSelect(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<Account>, newActiveId: String?) {
        items = newItems
        activeId = newActiveId
        notifyDataSetChanged()
    }
}
