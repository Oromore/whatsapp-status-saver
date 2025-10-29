package com.statussaver

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.statussaver.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.category_chat)

        // Send button click listener
        binding.btnSend.setOnClickListener {
            sendWhatsAppMessage()
        }
    }

    private fun sendWhatsAppMessage() {
        val phoneNumber = binding.etPhoneNumber.text.toString().trim()
        val message = binding.etMessage.text.toString().trim()

        // Validation
        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_phone_number), Toast.LENGTH_SHORT).show()
            binding.etPhoneNumber.requestFocus()
            return
        }

        if (message.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_message), Toast.LENGTH_SHORT).show()
            binding.etMessage.requestFocus()
            return
        }

        // Clean phone number (remove spaces, dashes, brackets, etc.)
        val cleanNumber = phoneNumber.replace("[^0-9+]".toRegex(), "")

        // Validate phone number format
        if (cleanNumber.length < 10) {
            Toast.makeText(this, getString(R.string.invalid_phone_number), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Create WhatsApp intent
            val intent = Intent(Intent.ACTION_VIEW)
            val url = "https://api.whatsapp.com/send?phone=$cleanNumber&text=${Uri.encode(message)}"
            intent.data = Uri.parse(url)
            
            // Try WhatsApp first
            intent.setPackage("com.whatsapp")
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                
                // Clear inputs after successfully opening WhatsApp
                binding.etPhoneNumber.text?.clear()
                binding.etMessage.text?.clear()
                Toast.makeText(this, getString(R.string.opening_whatsapp), Toast.LENGTH_SHORT).show()
            } else {
                // If WhatsApp not installed, try WhatsApp Business
                intent.setPackage("com.whatsapp.w4b")
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    
                    binding.etPhoneNumber.text?.clear()
                    binding.etMessage.text?.clear()
                    Toast.makeText(this, getString(R.string.opening_whatsapp), Toast.LENGTH_SHORT).show()
                } else {
                    // Neither installed
                    Toast.makeText(this, getString(R.string.whatsapp_not_installed), Toast.LENGTH_LONG).show()
                }
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_opening_whatsapp), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
