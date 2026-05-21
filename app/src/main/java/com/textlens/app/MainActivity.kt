package com.textlens.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.textlens.app.databinding.ActivityMainBinding
import com.yalantis.ucrop.UCrop
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var cameraImageUri: Uri? = null

    // ─── Permission launchers ─────────────────────────────────────────────────

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else toast("Camera permission required")
    }

    private val galleryPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchGallery() else toast("Storage permission required")
    }

    // ─── Activity result launchers ────────────────────────────────────────────

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageUri?.let { launchCrop(it) }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { launchCrop(it) }
    }

    private val cropLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { intent ->
                UCrop.getOutput(intent)?.let { croppedUri ->
                    navigateToResult(croppedUri)
                }
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        loadHistory()
    }

    private fun setupButtons() {
        binding.btnCamera.setOnClickListener { checkCameraAndLaunch() }
        binding.btnGallery.setOnClickListener { checkGalleryAndLaunch() }
        binding.btnClearHistory.setOnClickListener { clearHistory() }
    }

    // ─── Camera ───────────────────────────────────────────────────────────────

    private fun checkCameraAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> launchCamera()
            else -> cameraPermLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val imageFile = createImageFile()
        cameraImageUri = FileProvider.getUriForFile(
            this, "${packageName}.provider", imageFile
        )
        cameraLauncher.launch(cameraImageUri)
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile("IMG_${timestamp}_", ".jpg", storageDir)
    }

    // ─── Gallery ──────────────────────────────────────────────────────────────

    private fun checkGalleryAndLaunch() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        when {
            ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED -> launchGallery()
            else -> galleryPermLauncher.launch(permission)
        }
    }

    private fun launchGallery() {
        galleryLauncher.launch("image/*")
    }

    // ─── Crop ─────────────────────────────────────────────────────────────────

    private fun launchCrop(sourceUri: Uri) {
        val destFile = createImageFile()
        val destUri = Uri.fromFile(destFile)

        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true)
            setShowCropGrid(true)
            setShowCropFrame(true)
            setToolbarTitle("Adjust Image")
            setCompressionQuality(95)
        }

        val intent = UCrop.of(sourceUri, destUri)
            .withOptions(options)
            .getIntent(this)

        cropLauncher.launch(intent)
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private fun navigateToResult(imageUri: Uri) {
        Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_IMAGE_URI, imageUri.toString())
            startActivity(this)
        }
    }

    // ─── History ─────────────────────────────────────────────────────────────

    private fun loadHistory() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historySet = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        val historyList = historySet.toList().sortedByDescending { it.substringBefore("|") }

        if (historyList.isEmpty()) {
            binding.tvHistoryEmpty.visibility = android.view.View.VISIBLE
            binding.rvHistory.visibility = android.view.View.GONE
            return
        }

        binding.tvHistoryEmpty.visibility = android.view.View.GONE
        binding.rvHistory.visibility = android.view.View.VISIBLE

        val adapter = HistoryAdapter(historyList) { item ->
            val text = item.substringAfter("|")
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", text))
            toast("Copied!")
        }
        binding.rvHistory.adapter = adapter
    }

    private fun clearHistory() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_HISTORY).apply()
        loadHistory()
        toast("History cleared")
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val PREFS_NAME = "textlens_prefs"
        const val KEY_HISTORY = "ocr_history"
    }
}
