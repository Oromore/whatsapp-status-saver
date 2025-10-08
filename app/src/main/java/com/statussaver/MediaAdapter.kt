package com.statussaver

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.nativead.NativeAd
import com.statussaver.core.MediaItem
import com.statussaver.core.MediaType
import com.statussaver.core.getFormattedSize
import com.statussaver.databinding.ItemMediaBinding
import com.statussaver.databinding.ItemNativeAdBinding

class MediaAdapter(
    private val onSaveClick: (MediaItem) -> Unit,
    private val onItemClick: (MediaItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private val nativeAds = mutableListOf<NativeAd>()
    
    companion object {
        const val VIEW_TYPE_MEDIA = 0
        const val VIEW_TYPE_AD = 1
    }

    fun submitList(newItems: List<Any>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addNativeAd(nativeAd: NativeAd) {
        nativeAds.add(nativeAd)
        // Find next placeholder and replace with ad
        notifyDataSetChanged()
    }

    fun getNativeAdCount(): Int {
        return items.count { it is NativeAdPlaceholder }
    }

    fun destroyNativeAds() {
        nativeAds.forEach { it.destroy() }
        nativeAds.clear()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is MediaItem -> VIEW_TYPE_MEDIA
            is NativeAdPlaceholder -> VIEW_TYPE_AD
            else -> VIEW_TYPE_MEDIA
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MEDIA -> {
                val binding = ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                MediaViewHolder(binding)
            }
            VIEW_TYPE_AD -> {
                val binding = ItemNativeAdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                NativeAdViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is MediaViewHolder -> {
                val item = items[position] as MediaItem
                holder.bind(item)
            }
            is NativeAdViewHolder -> {
                if (nativeAds.isNotEmpty()) {
                    val ad = nativeAds.removeAt(0)
                    holder.bind(ad)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    // Media ViewHolder
    inner class MediaViewHolder(private val binding: ItemMediaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaItem) {
            binding.fileName.text = item.fileName
            binding.fileSize.text = item.getFormattedSize()

            // Load thumbnail
            Glide.with(binding.root.context)
                .load(item.path)
                .centerCrop()
                .into(binding.thumbnail)

            // Show play icon for videos
            binding.playIcon.visibility = if (item.type == MediaType.VIDEO) View.VISIBLE else View.GONE

            // Save button click
            binding.btnSave.setOnClickListener {
                onSaveClick(item)
            }

            // Card click
            binding.cardView.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    // Native Ad ViewHolder
    inner class NativeAdViewHolder(private val binding: ItemNativeAdBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(nativeAd: NativeAd) {
            val adView = binding.nativeAdView

            // Set ad assets
            adView.headlineView = binding.adHeadline
            adView.bodyView = binding.adBody
            adView.callToActionView = binding.adCallToAction
            adView.iconView = binding.adIcon
            adView.mediaView = binding.adMedia

            // Populate ad views
            binding.adHeadline.text = nativeAd.headline
            binding.adBody.text = nativeAd.body
            binding.adCallToAction.text = nativeAd.callToAction

            nativeAd.icon?.let {
                binding.adIcon.setImageDrawable(it.drawable)
                binding.adIcon.visibility = View.VISIBLE
            } ?: run {
                binding.adIcon.visibility = View.GONE
            }

            nativeAd.mediaContent?.let {
                binding.adMedia.setMediaContent(it)
                binding.adMedia.visibility = View.VISIBLE
            } ?: run {
                binding.adMedia.visibility = View.GONE
            }

            // Register the native ad
            adView.setNativeAd(nativeAd)
        }
    }
}
