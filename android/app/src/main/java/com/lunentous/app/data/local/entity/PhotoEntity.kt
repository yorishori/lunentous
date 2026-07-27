package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** timelineEventLocalId null = a plant's standalone avatar. localFileUri is
 * set immediately on capture (app-private storage, works offline);
 * remoteFilePath is set once the upload succeeds -- mirrors photos, plus
 * the local-capture bookkeeping the server has no concept of. */
@Entity(
    tableName = "photos",
    indices = [Index(value = ["serverId"], unique = true), Index(value = ["timelineEventLocalId"])],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val plantLocalId: Long,
    val timelineEventLocalId: Long? = null,
    val localFileUri: String? = null,
    val remoteFilePath: String? = null,
    val createdAt: String = "",
    val pendingSync: Boolean = false,
)
