package com.mydownloader.app

import android.app.Activity
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    private lateinit var urlInput: EditText
    private lateinit var audioRadio: RadioButton
    private lateinit var downloadBtn: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    @Volatile
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handleShare(intent)

        setStatus("Engine එක සූදානම් කරමින්... (පළමු වතාවේ විනාඩියක් විතර යනවා)")
        worker.execute {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                ready = true
                setStatus("සූදානම් ✅  Link එකක් දාලා Download ඔබන්න.")
            } catch (e: Exception) {
                setStatus("Engine error: ${e.message}")
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) urlInput.setText(text.trim())
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }

        val title = TextView(this).apply {
            text = "My Downloader"
            textSize = 26f
            setTextColor(Color.parseColor("#B71C1C"))
        }
        root.addView(title)

        urlInput = EditText(this).apply {
            hint = "Video link එක මෙතන paste කරන්න"
            setSingleLine(false)
            minLines = 2
        }
        root.addView(urlInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        val pasteBtn = Button(this).apply {
            text = "Paste"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val t = cm.primaryClip?.getItemAt(0)?.text?.toString()
                if (!t.isNullOrBlank()) urlInput.setText(t.trim())
            }
        }
        root.addView(pasteBtn)

        val group = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val videoRadio = RadioButton(this).apply { text = "Video (MP4)"; id = 1001 }
        audioRadio = RadioButton(this).apply { text = "Audio (MP3)"; id = 1002 }
        group.addView(videoRadio)
        group.addView(audioRadio)
        group.check(1001)
        root.addView(group, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        downloadBtn = Button(this).apply {
            text = "Download"
            setOnClickListener { startDownload() }
        }
        root.addView(downloadBtn, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        root.addView(progressBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) })

        statusText = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.START
        }
        root.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun setStatus(msg: String) {
        ui.post { statusText.text = msg }
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        if (url.isEmpty()) {
            Toast.makeText(this, "Link එකක් දාන්න", Toast.LENGTH_SHORT).show()
            return
        }
        if (!ready) {
            Toast.makeText(this, "Engine එක තාම සූදානම් නෑ, ටිකක් ඉන්න", Toast.LENGTH_SHORT).show()
            return
        }
        val audio = audioRadio.isChecked
        downloadBtn.isEnabled = false
        progressBar.progress = 0
        setStatus("පටන් ගන්නවා...")
        worker.execute { runDownload(url, audio) }
    }

    private fun runDownload(url: String, audio: Boolean) {
        val tmp = File(cacheDir, "dl_${System.currentTimeMillis()}")
        tmp.mkdirs()
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("--no-playlist")
            req.addOption("-o", tmp.absolutePath + "/%(title).80s.%(ext)s")
            if (audio) {
                req.addOption("-x")
                req.addOption("--audio-format", "mp3")
                req.addOption("--audio-quality", "0")
            } else {
                req.addOption("-f", "bv*[height<=720]+ba/b[height<=720]/b")
                req.addOption("--merge-output-format", "mp4")
            }

            YoutubeDL.getInstance().execute(req, null) { progress, eta, _ ->
                ui.post {
                    progressBar.progress = progress.toInt().coerceIn(0, 100)
                    statusText.text = "බාගනිමින්... ${progress.toInt().coerceIn(0, 100)}%  (තව තත්පර ${eta})"
                }
            }

            val allowed = setOf("mp4", "mp3", "m4a", "webm", "mkv", "opus", "ogg", "wav")
            var saved = 0
            var lastName = ""
            tmp.listFiles()?.forEach { f ->
                if (f.extension.lowercase() in allowed && saveToDownloads(f)) {
                    saved++
                    lastName = f.name
                }
            }
            if (saved > 0) {
                ui.post { progressBar.progress = 100 }
                setStatus("හරි ✅  Downloads > MyDownloader folder එකේ save වුණා:\n$lastName")
            } else {
                setStatus("File එකක් හම්බුණේ නෑ. Link එක පරීක්ෂා කරලා ආයෙ try කරන්න.")
            }
        } catch (e: Exception) {
            setStatus("Error: ${e.message}")
        } finally {
            tmp.deleteRecursively()
            ui.post { downloadBtn.isEnabled = true }
        }
    }

    private fun saveToDownloads(file: File): Boolean {
        val mime = when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "opus", "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/MyDownloader")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return false
        contentResolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: return false
        return true
    }
}
