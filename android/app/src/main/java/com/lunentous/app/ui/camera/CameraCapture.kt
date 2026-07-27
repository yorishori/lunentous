package com.lunentous.app.ui.camera

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lunentous.app.ui.photos.importImageToLocalFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/**
 * The Android Photo Picker (`ActivityResultContracts.PickVisualMedia`) --
 * needs no storage permission on any supported API level, unlike the
 * legacy `ACTION_GET_CONTENT`/`OPEN_DOCUMENT` pickers. The picked
 * content:// URI is only guaranteed readable for this request's lifetime,
 * so it's copied into the same app-private photos/ dir a capture writes
 * to (see ui/photos/ImageImport.kt) before `onPicked` fires.
 */
@Composable
fun rememberGalleryPickerLauncher(onPicked: (File) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) { importImageToLocalFile(context, uri) }
            if (file != null) onPicked(file)
        }
    }

    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

private fun createCaptureFile(context: Context): File {
    val photosDir = File(context.filesDir, "photos").apply { mkdirs() }
    return File(photosDir, "capture_${System.currentTimeMillis()}.jpg")
}

/**
 * A single "add photo" affordance offering both sources behind one button
 * (an M3 DropdownMenu) rather than two separate icon buttons -- used
 * everywhere a photo can be attached (timeline entries, plant avatars).
 */
@Composable
fun AddPhotoButton(onPhoto: (File) -> Unit, modifier: Modifier = Modifier) {
    var menuExpanded by remember { mutableStateOf(false) }
    val takePhoto = rememberCameraCaptureLauncher(onPhoto)
    val pickFromGallery = rememberGalleryPickerLauncher(onPhoto)

    Box(modifier = modifier) {
        OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
            Icon(Icons.Filled.AddAPhoto, contentDescription = "Add photo")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Take photo") },
                leadingIcon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
                onClick = { menuExpanded = false; takePhoto() },
            )
            DropdownMenuItem(
                text = { Text("Choose from gallery") },
                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                onClick = { menuExpanded = false; pickFromGallery() },
            )
        }
    }
}
