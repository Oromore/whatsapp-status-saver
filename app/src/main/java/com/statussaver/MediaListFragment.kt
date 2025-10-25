package com.statussaver

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.statussaver.core.FileSaver
import com.statussaver.core.MediaItem
import com.statussaver.core.StatusScanner
import com.statussaver.databinding.FragmentMediaListBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaListFragment : Fragment() {

    companion object {
        private const val TAG = "MediaListFragment"
        private const val ARG_MEDIA_TYPE = "media_type"

        fun newInstance(mediaType: String): MediaListFragment {
            return MediaListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MEDIA_TYPE, mediaType)
                }
            }
        }
    }

    private var _binding: FragmentMediaListBinding? = null
    private val binding get() = _binding!!

    private lateinit var scanner: StatusScanner
    private lateinit var fileSaver: FileSaver
    private lateinit var adapter: MediaAdapter
    private lateinit var interstitialAdManager: InterstitialAdManager
    private var mediaType: String = "IMAGE"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "=== MediaListFragment onViewCreated ===")

        scanner = StatusScanner(requireContext())
        fileSaver = FileSaver(requireContext())
        interstitialAdManager = InterstitialAdManager(requireActivity())

        // Get media type from arguments
        mediaType = arguments?.getString(ARG_MEDIA_TYPE) ?: "IMAGE"
        Log.d(TAG, "Media type: $mediaType")

        setupToolbar()
        setupRecyclerView()
        loadMedia()
    }

    private fun setupToolbar() {
        val title = when (mediaType) {
            "IMAGE" -> "Images"
            "VIDEO" -> "Videos"
            "AUDIO" -> "Audio"
            else -> "Media"
        }
        binding.toolbar.title = title
        binding.toolbar.setNavigationOnClickListener {
            // Go back to home screen
            (activity as? MainActivity)?.showHomeScreen()
        }
    }

    private fun setupRecyclerView() {
        adapter = MediaAdapter(
            onSaveClick = { item ->
                saveMedia(item)
            },
            onItemClick = { item ->
                openMediaViewer(item)
            }
        )

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MediaListFragment.adapter
        }
    }

    private fun loadMedia() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
        binding.emptyState.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mediaMap = scanner.scanAllStatus()
                val mediaList = mediaMap[mediaType] ?: emptyList()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE

                    if (mediaList.isNotEmpty()) {
                        binding.recyclerView.visibility = View.VISIBLE
                        adapter.submitList(mediaList)
                    } else {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.emptyText.text = "No ${mediaType.lowercase()} found"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyState.visibility = View.VISIBLE
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveMedia(item: MediaItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val success = fileSaver.saveToDownloads(item)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(requireContext(), "Saved to Downloads/WhatsAppStatus!", Toast.LENGTH_LONG).show()

                    // Track save for interstitial
                    interstitialAdManager.trackSave()
                } else {
                    Toast.makeText(requireContext(), "Failed to save", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openMediaViewer(item: MediaItem) {
        val intent = Intent(requireContext(), MediaViewerActivity::class.java)
        intent.putExtra("MEDIA_PATH", item.path)
        intent.putExtra("MEDIA_NAME", item.fileName)
        intent.putExtra("MEDIA_SIZE", item.size)
        intent.putExtra("MEDIA_TYPE", item.type.name)
        intent.putExtra("MEDIA_DATE", item.dateModified)
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
