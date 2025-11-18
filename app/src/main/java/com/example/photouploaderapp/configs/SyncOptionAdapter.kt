package com.example.photouploaderapp.configs

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.photouploaderapp.configs.SyncOption

class SyncOptionAdapter(
    private val syncOptions: List<SyncOption>,
    private val onItemSelectedListener: OnSyncItemSelectedListener
) : RecyclerView.Adapter<SyncOptionAdapter.SyncViewHolder>() {

    interface OnSyncItemSelectedListener {
        fun onItemSelected(option: SyncOption)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return SyncViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: SyncViewHolder, position: Int) {
        val option = syncOptions[position]
        holder.bind(option)
    }

    override fun getItemCount(): Int = syncOptions.size

    inner class SyncViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(option: SyncOption) {
            textView.text = option.title
            textView.typeface = if (option.isSelected) {
                option.typeface ?: Typeface.DEFAULT_BOLD.also { option.typeface = it }
            } else {
                option.typeface ?: Typeface.DEFAULT.also { option.typeface = it }
            }
        }

        init {
            itemView.setOnClickListener {
                val option = syncOptions[adapterPosition]
                option.isSelected = true
                syncOptions.forEach { other ->
                    if (other != option) other.isSelected = false
                }
                notifyItemChanged(adapterPosition)
                onItemSelectedListener.onItemSelected(option)
            }
        }
    }
}