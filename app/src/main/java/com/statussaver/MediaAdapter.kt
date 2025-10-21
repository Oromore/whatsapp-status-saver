package com.statussaver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.statussaver.core.MediaItem
import com.statussaver.core.MediaType
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for displaying media items (images, videos, audio)
 * Clean version - no AdMob native ads
 */
class MediaAdapter(
    private val onSaveClick: (MediaItem) -> Unit,
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaItem, MediaAdapter.MediaViewHolder>(MediaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
        private val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileSize: TextView = itemView.findViewById(R.id.fileSize)
        private val saveButton: View = itemView.findViewById(R.id.btnSave)

        fun bind(item: MediaItem) {
            // Load thumbnail
            Glide.with(itemView.context)
                .load(item.path)
                .centerCrop()
                .into(thumbnail)

            // Show play icon for videos
            playIcon.visibility = if (item.type == MediaType.VIDEO) View.VISIBLE else View.GONE

            // Set file info
            fileName.text = item.fileName
            fileSize.text = formatFileSize(item.size)

            // Click listeners
            itemView.setOnClickListener { onItemClick(item) }
            saveButton.setOnClickListener { onSaveClick(item) }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> "${bytes / (1024 * 1024)} MB"
            }
        }
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<MediaItem>() {
        override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem): Boolean {
            return oldItem == newItem
        }
    }
}
