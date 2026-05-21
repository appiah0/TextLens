package com.textlens.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Transparent trampoline activity.
 * Receives images shared from gallery, browser, WhatsApp, etc.,
 * then immediately launches ResultActivity for OCR.
 */
class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSingleImage(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleImages(intent)
            else -> {
                toast("Unsupported action")
                finish()
            }
        }
    }

    private fun handleSingleImage(intent: Intent) {
        val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        if (uri != null) {
            launchResult(uri)
        } else {
            toast("No image received")
            finish()
        }
    }

    private fun handleMultipleImages(intent: Intent) {
        // Process first image; could extend to batch OCR
        val uris: List<Uri> = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
        }

        if (uris.isNotEmpty()) {
            launchResult(uris.first())
        } else {
            toast("No images received")
            finish()
        }
    }

    private fun launchResult(uri: Uri) {
        Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_IMAGE_URI, uri.toString())
            startActivity(this)
        }
        finish() // Don't keep this activity in back stack
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
