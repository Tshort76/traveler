package dev.tlong.traveler.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Writes [text] to cacheDir/exports and opens the share sheet for it. */
fun shareTextFile(context: Context, fileName: String, text: String, title: String): Boolean = runCatching {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, fileName).apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, fileName)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(send, title)
        // Sharing a trip to Traveler itself would only re-import it as "already imported".
        .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, "${context.packageName}.MainActivity")))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}.isSuccess

/** Writes [text] to a document the traveler picked with the system "save as" screen. */
suspend fun writeToUri(context: Context, uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
    runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) } != null }.getOrDefault(false)
}

fun copyToClipboard(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * The optional overview picture: fetched once when online and kept in filesDir, so it is still
 * there in airplane mode. Returns null while loading, offline before the first fetch, or on any
 * failure — the map and the ordered list stand on their own without it.
 */
@Composable
fun rememberCachedImage(url: String?): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, url) {
        if (url == null) return@produceState
        value = withContext(Dispatchers.IO) { cachedBitmap(context, url)?.asImageBitmap() }
    }
}

private fun cachedBitmap(context: Context, url: String): Bitmap? {
    val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
    val file = File(File(context.filesDir, "images").apply { mkdirs() }, name)
    if (!file.exists()) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 15000
            conn.inputStream.use { input -> File(file.path + ".part").outputStream().use { input.copyTo(it) } }
            File(file.path + ".part").renameTo(file)
        }
    }
    return if (file.exists()) BitmapFactory.decodeFile(file.path) else null
}
