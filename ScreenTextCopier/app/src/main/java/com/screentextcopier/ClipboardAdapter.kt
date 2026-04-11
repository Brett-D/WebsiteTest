package com.screentextcopier

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.screentextcopier.model.ClipboardItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView adapter for the clipboard history list.
 *
 * @param onItemClick Called when an item is tapped (copies text to system clipboard).
 * @param onItemLongClick Called when an item is long-pressed (opens text selection).
 */
class ClipboardAdapter(
    private val onItemClick: (ClipboardItem) -> Unit,
    private val onItemLongClick: (ClipboardItem) -> Unit
) : ListAdapter<ClipboardItem, ClipboardAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.clipboard_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textPreview: TextView = itemView.findViewById(R.id.tv_item_text)
        private val timestamp: TextView = itemView.findViewById(R.id.tv_item_timestamp)
        private val thumbnail: ImageView = itemView.findViewById(R.id.iv_item_thumbnail)

        fun bind(item: ClipboardItem) {
            textPreview.text = item.textPreview()

            val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            timestamp.text = dateFormat.format(Date(item.timestamp))

            if (item.imagePath != null && File(item.imagePath).exists()) {
                thumbnail.visibility = View.VISIBLE
                thumbnail.load(File(item.imagePath))
            } else {
                thumbnail.visibility = View.GONE
            }

            itemView.setOnClickListener { onItemClick(item) }
            itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ClipboardItem>() {
        override fun areItemsTheSame(old: ClipboardItem, new: ClipboardItem) = old.id == new.id
        override fun areContentsTheSame(old: ClipboardItem, new: ClipboardItem) = old == new
    }
}
