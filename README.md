# TextLens — OCR App for Android

Extract text from any image. Works fully offline. Appears in the share sheet.

---

## Features

| Feature | Detail |
|---|---|
| 📷 Camera capture | Take photo directly in-app |
| 🖼 Gallery picker | Choose any image from device |
| ✂️ Crop before scan | Free-crop with grid overlay (UCrop) |
| 🔤 Offline OCR | Google ML Kit bundled — no internet needed |
| ✏️ Editable result | Fix/clean extracted text before copying |
| 📋 Copy all | One tap to clipboard |
| ☑️ Copy selected lines | Multi-select individual lines |
| 🔍 Filter lines | Search/filter lines by keyword |
| ⬆️ Share text | Send via any app |
| 📜 History | Last 50 scans saved locally |
| 🔗 **Share sheet** | Appears when sharing images from any app |
| 🌙 Dark mode | Auto follows system theme (Material 3) |

---

## Setup in Android Studio

1. **Open project**: File → Open → select `TextLens/` folder
2. **Sync Gradle**: Click "Sync Now" when prompted
3. **Run**: Select device/emulator → Run ▶️

Minimum Android version: **7.0 (API 24)**

---

## How the Share Sheet Works

The `ShareReceiverActivity` registers an `intent-filter` for:
```
action: android.intent.action.SEND
mimeType: image/*
```

This makes **TextLens appear in the share menu** of any app (Gallery, WhatsApp,
Chrome, Files, etc.) when the user shares an image. The activity is a transparent
trampoline — it receives the URI, passes it to `ResultActivity`, and finishes
immediately (doesn't pollute the back stack).

---

## OCR Engine Choice

**Used: `com.google.mlkit:text-recognition:16.0.0`** (bundled)

| Option | Offline | APK size | Accuracy |
|---|---|---|---|
| ML Kit bundled ✅ | ✅ Always | +4 MB | Excellent |
| ML Kit Play Services | ⚠️ First run needs download | Minimal | Excellent |
| Tesseract4Android | ✅ Always | +20 MB | Good |

ML Kit bundled is the best balance for this use case.

---

## Project Structure

```
app/src/main/java/com/textlens/app/
├── MainActivity.kt          — Home screen: camera, gallery, history
├── ResultActivity.kt        — OCR + result display + actions
├── ShareReceiverActivity.kt — Share sheet handler (transparent trampoline)
└── HistoryAdapter.kt        — RecyclerView adapter for history list

res/layout/
├── activity_main.xml
├── activity_result.xml
└── item_history.xml

res/xml/
└── file_paths.xml           — FileProvider paths for camera
```

---

## Extending the App

- **Multi-page PDF scan**: Integrate `iTextPDF` + batch OCR loop
- **Auto-language detection**: ML Kit can auto-detect; add `TextRecognizerOptions.Builder`
- **Export to file**: Use `DocumentFile` API to save `.txt` to Downloads
- **QR/Barcode**: Add `com.google.mlkit:barcode-scanning` alongside text recognition
- **Batch mode**: Extend `ShareReceiverActivity.handleMultipleImages()` to process all URIs
