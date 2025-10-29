package com.statussaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.statussaver.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    companion object {
        fun newInstance(): ChatFragment {
            return ChatFragment()
        }
    }

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupSendButton()
    }

    private fun setupToolbar() {
        binding.toolbar.title = "Chat"
        binding.toolbar.setNavigationOnClickListener {
            // Go back to home screen
            (activity as? MainActivity)?.showHomeScreen()
        }
    }

    private fun setupSendButton() {
        binding.btnSend.setOnClickListener {
            sendMessage()
        }
    }

    private fun sendMessage() {
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        val message = binding.etMessage.text.toString().trim()

        // Validation
        if (phoneNumber.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.enter_phone_number), Toast.LENGTH_SHORT).show()
            binding.etPhoneNumber.requestFocus()
            return
        }

        if (message.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.enter_message), Toast.LENGTH_SHORT).show()
            binding.etMessage.requestFocus()
            return
        }

        // Clean phone number (remove spaces, dashes, brackets, etc.)
        val cleanNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")

        // Validate phone number format
        if (cleanNumber.length < 10) {
            Toast.makeText(requireContext(), getString(R.string.invalid_phone_number), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Create WhatsApp intent
            val intent = Intent(Intent.ACTION_VIEW)
            val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}"
            intent.data = Uri.parse(url)
            
            // Try WhatsApp first
            intent.setPackage("com.whatsapp")
            
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
                
                // Clear inputs after successfully opening WhatsApp
                binding.etPhoneNumber.text?.clear()
                binding.etMessage.text?.clear()
                Toast.makeText(requireContext(), getString(R.string.opening_whatsapp), Toast.LENGTH_SHORT).show()
            } else {
                // If WhatsApp not installed, try WhatsApp Business
                intent.setPackage("com.whatsapp.w4b")
                if (intent.resolveActivity(requireActivity().packageManager) != null) {
                    startActivity(intent)
                    
                    binding.etPhoneNumber.text?.clear()
                    binding.etMessage.text?.clear()
                    Toast.makeText(requireContext(), getString(R.string.opening_whatsapp), Toast.LENGTH_SHORT).show()
                } else {
                    // Neither installed
                    Toast.makeText(requireContext(), getString(R.string.whatsapp_not_installed), Toast.LENGTH_LONG).show()
                }
            }
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.error_opening_whatsapp), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
