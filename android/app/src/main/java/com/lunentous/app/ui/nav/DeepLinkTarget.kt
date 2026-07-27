package com.lunentous.app.ui.nav

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable

/** Where an external entry point (home screen widget tap, app shortcut,
 * share-to-app, notification tap) wants the app to land -- parsed once
 * from the launching/new Intent and consumed by MainScaffold's NavHost. */
sealed interface DeepLinkTarget {
    data class PlantDetail(val plantLocalId: Long) : DeepLinkTarget
    data object Calendar : DeepLinkTarget
    data object NewTimelineEntry : DeepLinkTarget
    data class ShareImage(val uri: Uri) : DeepLinkTarget
}

const val EXTRA_PLANT_LOCAL_ID = "com.lunentous.app.extra.PLANT_LOCAL_ID"
const val EXTRA_DESTINATION = "com.lunentous.app.extra.DESTINATION"
const val DESTINATION_CALENDAR = "calendar"
const val DESTINATION_NEW_ENTRY = "new_entry"

fun parseDeepLink(intent: Intent?): DeepLinkTarget? {
    intent ?: return null

    if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
        val uri = intent.getParcelableExtraCompat<Uri>(Intent.EXTRA_STREAM)
        if (uri != null) return DeepLinkTarget.ShareImage(uri)
    }

    val plantLocalId = intent.getLongExtra(EXTRA_PLANT_LOCAL_ID, -1L)
    if (plantLocalId != -1L) return DeepLinkTarget.PlantDetail(plantLocalId)

    return when (intent.getStringExtra(EXTRA_DESTINATION)) {
        DESTINATION_CALENDAR -> DeepLinkTarget.Calendar
        DESTINATION_NEW_ENTRY -> DeepLinkTarget.NewTimelineEntry
        else -> null
    }
}

/** getParcelableExtra's typed overload is API 33+; minSdk here is 26. */
private inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }
