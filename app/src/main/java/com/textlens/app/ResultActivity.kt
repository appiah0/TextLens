package com.textlens.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.textlens.app.databinding.ActivityResultBinding
import java.text.SimpleDateFormat
import java.util.*

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private var extractedText = ""
    private var imageUri: Uri? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Extracted Text"

        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString == null) {
            toast("No image provided")
            finish()
            return
        }

        imageUri = Uri.parse(uriString)
        binding.imagePreview.load(imageUri)

        setupTextEditor()
        runOcr(imageUri!!)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.result_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_copy_all -> { copyAll(); true }
            R.id.action_share -> { shareText(); true }
            R.id.action_clear -> { clearText(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ─── OCR ──────────────────────────────────────────────────────────────────

    private fun runOcr(uri: Uri) {
        showLoading(true)

        try {
            val image = InputImage.fromFilePath(this, uri)

            // Bundled recognizer — works 100% offline
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    showLoading(false)
                    extractedText = visionText.text

                    if (extractedText.isBlank()) {
                        showEmptyState()
                    } else {
                        showResult(extractedText)
                        saveToHistory(extractedText)
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    showError("OCR failed: ${e.message}")
                }
        } catch (e: Exception) {
            showLoading(false)
            showError("Could not load image: ${e.message}")
        }
    }

    // ─── UI states ────────────────────────────────────────────────────────────

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.layoutResult.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun showResult(text: String) {
        binding.tvEmpty.visibility = View.GONE
        binding.etResult.visibility = View.VISIBLE
        binding.etResult.setText(text)
        updateWordCount(text)
        binding.btnCopy.isEnabled = true
        binding.btnShare.isEnabled = true
    }

    private fun showEmptyState() {
        binding.tvEmpty.visibility = View.VISIBLE
        binding.etResult.visibility = View.GONE
        binding.btnCopy.isEnabled = false
        binding.btnShare.isEnabled = false
        binding.tvWordCount.text = "No text found"
    }

    private fun showError(msg: String) {
        binding.tvEmpty.text = msg
        binding.tvEmpty.visibility = View.VISIBLE
        binding.etResult.visibility = View.GONE
    }

    // ─── Text editor setup ────────────────────────────────────────────────────

    private fun setupTextEditor() {
        // Live word count
        binding.etResult.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateWordCount(s.toString())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Quick action buttons
        binding.btnCopy.setOnClickListener { copyAll() }
        binding.btnShare.setOnClickListener { shareText() }
        binding.btnCopyLines.setOnClickListener { showCopyLinesDialog() }

        // Search/filter
        binding.btnSearch.setOnClickListener { toggleSearch() }
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filterLines(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun updateWordCount(text: String) {
        if (text.isBlank()) {
            binding.tvWordCount.text = "0 words · 0 chars"
            return
        }
        val words = text.trim().split(Regex("\\s+")).size
        val chars = text.length
        binding.tvWordCount.text = "$words words · $chars chars"
    }

    // ─── Copy options ─────────────────────────────────────────────────────────

    private fun copyAll() {
        val text = binding.etResult.text.toString()
        if (text.isBlank()) return
        copyToClipboard(text)
        toast("All text copied!")
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
    }

    private fun clearText() {
        binding.etResult.setText("")
        extractedText = ""
    }

    private fun showCopyLinesDialog() {
        val text = binding.etResult.text.toString()
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return

        val items = lines.take(50).toTypedArray() // Show up to 50 lines
        val checked = BooleanArray(items.size) { true }

        android.app.AlertDialog.Builder(this)
            .setTitle("Select lines to copy")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Copy selected") { _, _ ->
                val selected = items.filterIndexed { i, _ -> checked[i] }.joinToString("\n")
                copyToClipboard(selected)
                toast("${checked.count { it }} lines copied!")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Search/Filter ────────────────────────────────────────────────────────

    private var searchVisible = false

    private fun toggleSearch() {
        searchVisible = !searchVisible
        binding.layoutSearch.visibility = if (searchVisible) View.VISIBLE else View.GONE
        if (!searchVisible) {
            binding.etSearch.setText("")
            showResult(extractedText) // Restore full text
        }
    }

    private fun filterLines(query: String) {
        if (query.isBlank()) {
            binding.etResult.setText(extractedText)
            return
        }
        val filtered = extractedText.lines()
            .filter { it.contains(query, ignoreCase = true) }
            .joinToString("\n")
        binding.etResult.setText(filtered)
        updateWordCount(filtered)
    }

    // ─── Share ────────────────────────────────────────────────────────────────

    private fun shareText() {
        val text = binding.etResult.text.toString()
        if (text.isBlank()) return
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            startActivity(Intent.createChooser(this, "Share text via"))
        }
    }

    // ─── History ─────────────────────────────────────────────────────────────

    private fun saveToHistory(text: String) {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(MainActivity.KEY_HISTORY, mutableSetOf())!!.toMutableSet()

        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())
        val preview = text.take(200).replace("\n", " ")
        val entry = "$timestamp|$preview"

        existing.add(entry)

        // Keep only last 50 entries
        val trimmed = existing.sortedByDescending { it.substringBefore("|") }.take(50).toSet()

        prefs.edit().putStringSet(MainActivity.KEY_HISTORY, trimmed).apply()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}
