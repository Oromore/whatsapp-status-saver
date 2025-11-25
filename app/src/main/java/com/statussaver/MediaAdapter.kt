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
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.nativeads.NativeAd
import com.yandex.mobile.ads.nativeads.NativeAdEventListener
import com.yandex.mobile.ads.nativeads.NativeAdLoadListener
import com.yandex.mobile.ads.nativeads.NativeAdLoader
import com.yandex.mobile.ads.nativeads.NativeAdRequestConfiguration
import com.yandex.mobile.ads.nativeads.NativeAdView
import com.yandex.mobile.ads.nativeads.NativeAdViewBinder

/**
 * Media adapter with Yandex native ads (MANUAL CONFIGURATION - OFFICIAL)
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
        private const val AD_FREQUENCY = 3
        
        private const val TEST_AD_UNIT_ID = "demo-native-content-yandex"
        private const val PROD_AD_UNIT_ID = "R-M-17685522-1"
    }

    sealed class ListItem {
        data class Media(val mediaItem: MediaItem) : ListItem()
        data class NativeAdItem(var nativeAd: NativeAd? = null, val position: Int) : ListItem()
    }

    private val loadedAds = mutableMapOf<Int, NativeAd>()
    private val loaders = mutableMapOf<Int, NativeAdLoader>()
    
    private val adUnitId: String
        get() = if (YandexAdsManager.TEST_MODE) TEST_AD_UNIT_ID else PROD_AD_UNIT_ID

    fun setMediaItems(mediaItems: List<MediaItem>) {
        val itemsWithAds = mutableListOf<ListItem>()

        mediaItems.forEachIndexed { index, mediaItem ->
            itemsWithAds.add(ListItem.Media(mediaItem))
            
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
            Log.w(TAG, "Yandex not ready - skipping native ad")
            return
        }

        Log.d(TAG, "Loading native ad for position $position")
        Log.d(TAG, "Ad Unit ID: $adUnitId")
        Log.d(TAG, "Test Mode: ${YandexAdsManager.TEST_MODE}")

        val nativeAdLoader = NativeAdLoader(context)
        loaders[position] = nativeAdLoader

        nativeAdLoader.setNativeAdLoadListener(object : NativeAdLoadListener {
            override fun onAdLoaded(nativeAd: NativeAd) {
                Log.d(TAG, "✓✓✓ NATIVE AD LOADED for position $position ✓✓✓")
                loadedAds[position] = nativeAd
                
                val currentList = currentList.toMutableList()
                if (position < currentList.size && currentList[position] is ListItem.NativeAdItem) {
                    currentList[position] = ListItem.NativeAdItem(nativeAd, position)
                    submitList(currentList)
                }
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                Log.e(TAG, "✗✗✗ NATIVE AD FAILED for position $position ✗✗✗")
                Log.e(TAG, "Error Code: ${error.code}")
                Log.e(TAG, "Error Description: ${error.description}")
            }
        })

        val adRequestConfiguration = NativeAdRequestConfiguration.Builder(adUnitId).build()
        nativeAdLoader.loadAd(adRequestConfiguration)
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

    // ========== Native Ad ViewHolder (MANUAL BINDING - OFFICIAL) ==========
    inner class NativeAdViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nativeAdView: NativeAdView = itemView.findViewById(R.id.nativeAdView)

        fun bind(nativeAd: NativeAd?) {
            if (nativeAd == null) {
                Log.w(TAG, "Waiting for native ad at position ${bindingAdapterPosition}")
                nativeAdView.visibility = View.GONE
                return
            }

            try {
                Log.d(TAG, "Binding native ad at position ${bindingAdapterPosition}")
                
                // Build the NativeAdViewBinder with all view mappings
                val binder = NativeAdViewBinder.Builder(nativeAdView)
                    .setMediaView(itemView.findViewById(R.id.mediaView))
                    .setIconView(itemView.findViewById(R.id.iconView))
                    .setTitleView(itemView.findViewById(R.id.titleView))
                    .setBodyView(itemView.findViewById(R.id.bodyView))
                    .setCallToActionView(itemView.findViewById<MaterialButton>(R.id.callToActionView))
                    .setSponsoredView(itemView.findViewById(R.id.sponsoredView))
                    .setWarningView(itemView.findViewById(R.id.warningView))
                    .setFeedbackView(itemView.findViewById(R.id.feedbackView))
                    .setAgeView(itemView.findViewById(R.id.ageView))
                    .setDomainView(itemView.findViewById(R.id.domainView))
                    .setFaviconView(itemView.findViewById(R.id.faviconView))
                    .setPriceView(itemView.findViewById(R.id.priceView))
                    .setRatingView(itemView.findViewById(R.id.ratingView))
                    .setReviewCountView(itemView.findViewById(R.id.reviewCountView))
                    .build()
                
                // Bind the ad using the official Yandex method
                nativeAd.bindNativeAd(binder)
                
                // Set event listener for tracking
                nativeAd.setNativeAdEventListener(object : NativeAdEventListener {
                    override fun onAdClicked() {
                        Log.d(TAG, "Native ad clicked")
                    }

                    override fun onLeftApplication() {
                        Log.d(TAG, "Left application from native ad")
                    }

                    override fun onReturnedToApplication() {
                        Log.d(TAG, "Returned to application")
                    }

                    override fun onImpression(data: com.yandex.mobile.ads.common.ImpressionData?) {
                        Log.d(TAG, "Native ad impression recorded")
                        data?.let {
                            Log.d(TAG, "Impression data: ${it.rawData}")
                        }
                    }
                })
                
                nativeAdView.visibility = View.VISIBLE
                Log.d(TAG, "✓ Native ad bound successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error binding native ad", e)
                nativeAdView.visibility = View.GONE
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
