package com.lunentous.app.ui.camera

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Delegates to the system camera app (ActivityResultContracts.TakePicture,
 * per the Android plan) rather than an in-app CameraX preview -- simpler,
 * and means this app never needs the CAMERA permission itself. Writes
 * directly into app-private files/photos/ (not cacheDir, which the system
 * can purge under storage pressure) so a captured photo survives however
 * long it takes the outbox to actually upload it while offline.
 *
 * Returns a trigger function the caller invokes to start a capture;
 * `onCaptured` fires with the resulting file only on a successful capture
 * (a cancelled camera intent is silently ignored, matching how e.g. a
 * cancelled file picker is normally handled).
 */
@Composable
fun rememberCameraCaptureLauncher(onCaptured: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null) onCaptured(file)
    }

    return {
        val file = createCaptureFile(context)
        pendingFile = file
        launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
    }
}

private fun createCaptureFile(context: Context): File {
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    return File(photosDir, "capture_${System.currentTimeMillis()}.jpg")
}
