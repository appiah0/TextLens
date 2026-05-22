package com.textlens.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.textlens.app.databinding.ActivityResultBinding
import java.text.SimpleDateFormat
import java.util.*

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding
    private var visionText: Text? = null
    private var imageBitmap: Bitmap? = null
    private var imageUri: Uri? = null
    private var mode = Mode.LENS
    enum class Mode { LENS, TEXT }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "TextLens"
        val uriString = intent.getStringExtra(EXTRA_IMAGE_URI) ?: run { finish(); return }
        imageUri = Uri.parse(uriString)
        setupButtons()
        loadImageAndRunOcr()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.result_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        android.R.id.home -> { finish(); true }
        R.id.action_copy_all -> { copyAll(); true }
        R.id.action_select_all -> { selectAll(); true }
        R.id.action_share -> { shareText(); true }
        R.id.action_switch_mode -> { toggleMode(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setupButtons() {
        binding.btnCopy.setOnClickListener { copySelected() }
        binding.btnCopyAll.setOnClickListener { copyAll() }
        binding.btnShare.setOnClickListener { shareText() }
        binding.btnSelectAll.setOnClickListener { selectAll() }
        binding.btnClearSel.setOnClickListener { binding.overlayView.clearSelection(); updateSelectionBar("") }
        binding.btnSwitchMode.setOnClickListener { toggleMode() }
        binding.overlayView.onSelectionChanged = { selectedText -> updateSelectionBar(selectedText) }
    }

    private fun loadImageAndRunOcr() {
        showLoading(true)
        try {
            val stream = contentResolver.openInputStream(imageUri!!)
            imageBitmap = BitmapFactory.decodeStream(stream)
            binding.imageView.setImageBitmap(imageBitmap)
            val image = InputImage.fromFilePath(this, imageUri!!)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    showLoading(false)
                    visionText = result
                    if (result.text.isBlank()) showEmpty()
                    else { saveToHistory(result.text); setupOverlay(); showLensMode() }
                }
                .addOnFailureListener { showLoading(false); toast("OCR failed: ${it.message}") }
        } catch (e: Exception) { showLoading(false); toast("Could not load image") }
    }

    private fun setupOverlay() {
        binding.imageView.post {
            val bmp = imageBitmap ?: return@post
            val text = visionText ?: return@post
            binding.overlayView.setOcrResult(text, bmp, binding.imageView.width, binding.imageView.height)
        }
    }

    private fun showLensMode() {
        mode = Mode.LENS
        binding.lensContainer.visibility = View.VISIBLE
        binding.textContainer.visibility = View.GONE
        binding.overlayView.visibility = View.VISIBLE
        binding.btnSwitchMode.text = "📝 Text"
        supportActionBar?.subtitle = "Tap words · Drag to select"
    }

    private fun showTextMode() {
        mode = Mode.TEXT
        binding.lensContainer.visibility = View.GONE
        binding.textContainer.visibility = View.VISIBLE
        binding.overlayView.visibility = View.GONE
        binding.etResult.setText(visionText?.text ?: "")
        binding.btnSwitchMode.text = "🔍 Lens"
        supportActionBar?.subtitle = "Edit extracted text"
    }

    private fun toggleMode() { if (mode == Mode.LENS) showTextMode() else showLensMode() }

    private fun updateSelectionBar(text: String) {
        if (text.isBlank()) { binding.selectionBar.visibility = View.GONE }
        else {
            binding.selectionBar.visibility = View.VISIBLE
            binding.tvSelected.text = "\"${if (text.length > 40) text.take(40) + "…" else text}\""
        }
    }

    private fun copySelected() {
        val text = if (mode == Mode.LENS) binding.overlayView.getSelectedText() else binding.etResult.text.toString()
        if (text.isBlank()) { toast("Nothing selected"); return }
        copyToClipboard(text); toast("Copied!")
    }

    private fun copyAll() {
        val text = visionText?.text ?: binding.etResult.text.toString()
        if (text.isBlank()) return
        copyToClipboard(text); binding.overlayView.selectAll(); toast("All text copied!")
    }

    private fun selectAll() {
        if (mode == Mode.LENS) binding.overlayView.selectAll() else { binding.etResult.selectAll(); toast("All selected") }
    }

    private fun shareText() {
        val text = visionText?.text ?: return
        Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); startActivity(Intent.createChooser(this, "Share text via")) }
    }

    private fun copyToClipboard(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("OCR Text", text))
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.mainContent.visibility = if (loading) View.GONE else View.VISIBLE
    }

    private fun showEmpty() { binding.tvEmpty.visibility = View.VISIBLE; binding.lensContainer.visibility = View.GONE }

    private fun saveToHistory(text: String) {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(MainActivity.KEY_HISTORY, mutableSetOf())!!.toMutableSet()
        val entry = "${SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date())}|${text.take(200).replace("\n", " ")}"
        existing.add(entry)
        prefs.edit().putStringSet(MainActivity.KEY_HISTORY, existing.sortedByDescending { it.substringBefore("|") }.take(50).toSet()).apply()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object { const val EXTRA_IMAGE_URI = "extra_image_uri" }
}
