package com.statussaver

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.statussaver.core.MediaItem
import com.statussaver.core.MediaType
import com.yandex.mobile.ads.common.AdRequestConfiguration
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.nativeads.NativeAd
import com.yandex.mobile.ads.nativeads.NativeAdEventListener
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdView
import com.yandex.mobile.ads.nativeads.NativeAdViewBinder

/**
 * Media adapter with Yandex native ads
 * Shows native ad every 3 media items
 */
class MediaAdapter(
    private val context: Context,
    private val onSaveClick: (MediaItem) -> Unit,
    private val onItemClick: (MediaItem) -> Unit
) : ListAdapter<MediaAdapter.ListItem, RecyclerView.ViewHolder>(ListItemDiffCallback()) {

    companion object {
        private const val TAG = "MediaAdapter"
        private const val VIEW_TYPE_MEDIA = 0
        private const val VIEW_TYPE_NATIVE_AD = 1
        private const val AD_FREQUENCY = 3 // Ad every 3 items
        private const val AD_UNIT_ID = "R-M-17685522-1"
    }

    sealed class ListItem {
        data class Media(val mediaItem: MediaItem) : ListItem()
        data class NativeAdItem(var nativeAd: NativeAd? = null, val position: Int) : ListItem()
    }

    private val loadedAds = mutableMapOf<Int, NativeAd>()

    fun setMediaItems(mediaItems: List<MediaItem>) {
        val itemsWithAds = mutableListOf<ListItem>()

        mediaItems.forEachIndexed { index, mediaItem ->
            itemsWithAds.add(ListItem.Media(mediaItem))

            // Insert ad after every 3 media items
            if ((index + 1) % AD_FREQUENCY == 0) {
                val adPosition = itemsWithAds.size
                itemsWithAds.add(ListItem.NativeAdItem(loadedAds[adPosition], adPosition))

                if (loadedAds[adPosition] == null) {
                    loadNativeAd(adPosition)
                }
            }
        }

        submitList(itemsWithAds)
    }

    private fun loadNativeAd(position: Int) {
        if (!YandexAdsManager.isReady()) {
            Log.w(TAG, "Yandex not ready - skipping ad load")
            return
        }

        Log.d(TAG, "Loading native ad for position $position")

        val adRequestConfig = AdRequestConfiguration.Builder(AD_UNIT_ID).build()
        val nativeAdLoader = NativeAdLoader(context)

        nativeAdLoader.setNativeAdLoadListener(object : NativeAdLoadListener {
            override fun onAdLoaded(nativeAd: NativeAd) {
                Log.d(TAG, "✓ Native ad loaded for position $position")
                loadedAds[position] = nativeAd

                // Update list if position still exists
                val currentList = currentList.toMutableList()
                if (position < currentList.size && currentList[position] is ListItem.NativeAdItem) {
                    currentList[position] = ListItem.NativeAdItem(nativeAd, position)
                    submitList(currentList)
                }
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                Log.e(TAG, "✗ Native ad failed for position $position: ${error.description}")
                
                // Remove ad placeholder on failure
                val currentList = currentList.toMutableList()
                if (position < currentList.size && currentList[position] is ListItem.NativeAdItem) {
                    currentList.removeAt(position)
                    submitList(currentList)
                }
            }
        })

        nativeAdLoader.loadAd(adRequestConfig)
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.Media -> VIEW_TYPE_MEDIA
            is ListItem.NativeAdItem -> VIEW_TYPE_NATIVE_AD
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_MEDIA -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_media, parent, false)
                MediaViewHolder(view)
            }
            VIEW_TYPE_NATIVE_AD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_native_ad, parent, false)
                NativeAdViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.Media -> (holder as MediaViewHolder).bind(item.mediaItem)
            is ListItem.NativeAdItem -> (holder as NativeAdViewHolder).bind(item.nativeAd)
        }
    }

    // ========== Media ViewHolder ==========
    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.thumbnail)
        private val playIcon: ImageView = itemView.findViewById(R.id.playIcon)
        private val fileName: TextView = itemView.findViewById(R.id.fileName)
        private val fileSize: TextView = itemView.findViewById(R.id.fileSize)
        private val saveButton: View = itemView.findViewById(R.id.btnSave)

        fun bind(item: MediaItem) {
            Glide.with(itemView.context)
                .load(item.path)
                .centerCrop()
                .into(thumbnail)

            playIcon.visibility = if (item.type == MediaType.VIDEO) View.VISIBLE else View.GONE
            fileName.text = item.fileName
            fileSize.text = formatFileSize(item.size)

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

    // ========== Native Ad ViewHolder ==========
    inner class NativeAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nativeAdView: NativeAdView = itemView.findViewById(R.id.nativeAdView)

        fun bind(nativeAd: NativeAd?) {
            if (nativeAd == null) return

            try {
                val binder = NativeAdViewBinder.Builder(nativeAdView)
                    .setAgeView(itemView.findViewById(R.id.ageView))
                    .setBodyView(itemView.findViewById(R.id.bodyView))
                    .setCallToActionView(itemView.findViewById<MaterialButton>(R.id.callToActionView))
                    .setDomainView(itemView.findViewById(R.id.domainView))
                    .setFaviconView(itemView.findViewById(R.id.faviconView))
                    .setFeedbackView(itemView.findViewById(R.id.feedbackView))
                    .setIconView(itemView.findViewById(R.id.iconView))
                    .setMediaView(itemView.findViewById(R.id.mediaView))
                    .setPriceView(itemView.findViewById(R.id.priceView))
                    .setRatingView(itemView.findViewById(R.id.ratingView))
                    .setReviewCountView(itemView.findViewById(R.id.reviewCountView))
                    .setSponsoredView(itemView.findViewById(R.id.sponsoredView))
                    .setTitleView(itemView.findViewById(R.id.titleView))
                    .setWarningView(itemView.findViewById(R.id.warningView))
                    .build()

                nativeAd.bindNativeAd(binder)

                nativeAd.setNativeAdEventListener(object : NativeAdEventListener {
                    override fun onAdClicked() {
                        Log.d(TAG, "Native ad clicked")
                    }

                    override fun onLeftApplication() {
                        Log.d(TAG, "Left application")
                    }

                    override fun onReturnedToApplication() {
                        Log.d(TAG, "Returned to application")
                    }

                    override fun onImpression(data: com.yandex.mobile.ads.common.ImpressionData?) {
                        Log.d(TAG, "Native ad impression")
                    }
                })

                Log.d(TAG, "Native ad bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error binding native ad", e)
            }
        }
    }

    class ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return when {
                oldItem is ListItem.Media && newItem is ListItem.Media ->
                    oldItem.mediaItem.path == newItem.mediaItem.path
                oldItem is ListItem.NativeAdItem && newItem is ListItem.NativeAdItem ->
                    oldItem.position == newItem.position
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return when {
                oldItem is ListItem.Media && newItem is ListItem.Media ->
                    oldItem == newItem
                oldItem is ListItem.NativeAdItem && newItem is ListItem.NativeAdItem ->
                    oldItem.nativeAd == newItem.nativeAd
                else -> false
            }
        }
    }
}
