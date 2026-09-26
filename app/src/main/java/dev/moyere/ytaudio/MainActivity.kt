package dev.moyere.ytaudio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var urlInput: EditText
    private lateinit var downloadButton: Button
    private lateinit var clearButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var errorText: TextView

    @Volatile private var ready = false
    private val prefs by lazy { getSharedPreferences("ytaudio", MODE_PRIVATE) }

    companion object {
        private const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000  // 1 fois par jour max
        private const val KEY_LAST_UPDATE = "last_ytdlp_update"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        downloadButton = findViewById(R.id.downloadButton)
        clearButton = findViewById(R.id.clearButton)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        errorText = findViewById(R.id.errorText)

        downloadButton.setOnClickListener { startDownload() }
        clearButton.setOnClickListener {
            urlInput.text.clear()
            hideError()
            urlInput.requestFocus()
        }
        urlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearButton.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { startDownload(); true } else false
        }

        handleShareIntent(intent)
        initEngine()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Récupère le lien quand on fait "Partager" depuis l'appli YouTube. */
    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val url = Regex("https?://\\S+").find(text)?.value ?: text
            urlInput.setText(url.trim())
        }
    }

    /** Prépare yt-dlp + ffmpeg (décompression au 1er lancement, quelques secondes). */
    private fun initEngine() {
        setBusy(true, "Préparation du moteur…")
        thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                ready = true
                runOnUiThread { setBusy(false, "Prêt.") }
            } catch (e: Throwable) {
                runOnUiThread {
                    setBusy(false, "Échec de l'initialisation.")
                    showError(e)
                }
            }
        }
    }

    private fun startDownload() {
        val url = urlInput.text.toString().trim()
        hideError()
        if (!ready) { statusText.text = "Patiente, le moteur se prépare…"; return }
        if (!url.startsWith("http")) {
            showError("URL invalide : colle un lien YouTube complet (https://…).")
            return
        }

        setBusy(true, "Démarrage…")
        progressBar.isIndeterminate = true

        thread {
            val workDir = File(cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
            try {
                // Mise à jour de yt-dlp au plus une fois par jour
                val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
                if (System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS) {
                    updateYtDlp()
                }

                try {
                    runDownload(url, workDir)
                } catch (e: YoutubeDLException) {
                    // Échec : souvent dû à un changement côté YouTube.
                    // On force une mise à jour de yt-dlp et on réessaie une fois.
                    if (!updateYtDlp()) throw e
                    workDir.listFiles()?.forEach { it.deleteRecursively() }
                    runDownload(url, workDir)
                }

                val mp3 = workDir.listFiles()?.firstOrNull { it.extension.equals("mp3", true) }
                    ?: throw IllegalStateException("Aucun fichier MP3 produit.")

                ui { progressBar.isIndeterminate = true; statusText.text = "Enregistrement…" }
                saveToMusic(mp3)

                ui {
                    setBusy(false, "✅ Enregistré dans Musique/YT Audio :\n${mp3.name}")
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                }
            } catch (e: Throwable) {
                ui {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                    setBusy(false, "❌ Échec du téléchargement.")
                    showError(e)
                }
            } finally {
                workDir.deleteRecursively()
            }
        }
    }

    /** Met à jour yt-dlp. Renvoie true si une mise à jour a été faite ou vérifiée. */
    private fun updateYtDlp(): Boolean {
        ui { progressBar.isIndeterminate = true; statusText.text = "Mise à jour de yt-dlp…" }
        return try {
            YoutubeDL.getInstance()
                .updateYoutubeDL(applicationContext, YoutubeDL.UpdateChannel.STABLE)
            prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
            true
        } catch (_: Throwable) {
            false // pas bloquant : on garde la version actuelle
        }
    }

    private fun runDownload(url: String, workDir: File) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("-x")                       // audio uniquement
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "0")     // meilleure qualité VBR
            addOption("--embed-metadata")
            addOption("-o", "${workDir.absolutePath}/%(title).150B.%(ext)s")
        }
        ui { statusText.text = "Démarrage…" }
        YoutubeDL.getInstance().execute(request, null, false) { progress, eta, line ->
            ui {
                when {
                    line.contains("[ExtractAudio]") -> {
                        progressBar.isIndeterminate = true
                        statusText.text = "Conversion en MP3…"
                    }
                    progress >= 0f -> {
                        progressBar.isIndeterminate = false
                        progressBar.progress = progress.toInt()
                        statusText.text = "Téléchargement : ${progress.toInt()} %" +
                            if (eta > 0) " (reste ${eta}s)" else ""
                    }
                }
            }
        }
    }

    /** Copie le MP3 dans le dossier public Musique/YT Audio (aucune permission requise). */
    private fun saveToMusic(file: File) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/YT Audio")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Impossible de créer le fichier dans Musique.")
        try {
            resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Throwable) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    // --- UI helpers ---

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun setBusy(busy: Boolean, status: String) {
        downloadButton.isEnabled = !busy
        clearButton.isEnabled = !busy
        urlInput.isEnabled = !busy
        statusText.text = status
    }

    private fun showError(e: Throwable) {
        val msg = buildString {
            append(e.javaClass.simpleName).append(": ").append(e.message ?: "(sans message)")
            e.cause?.let { append("\n\nCause : ").append(it.message) }
        }
        showError(msg)
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = View.VISIBLE
    }

    private fun hideError() {
        errorText.text = ""
        errorText.visibility = View.GONE
    }
}
