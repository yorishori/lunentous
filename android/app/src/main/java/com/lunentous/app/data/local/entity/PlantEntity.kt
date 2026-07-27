package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local-ID-first: `localId` is the permanent Room PK and is what every
 * other entity's foreign keys point at; `serverId` is nullable and only
 * populated once this row has been created on the server. See the Android
 * plan's "Room schema -- local-ID-first, server-ID lazy" for why.
 */
@Entity(tableName = "plants", indices = [Index(value = ["serverId"], unique = true)])
data class PlantEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val serverId: Long? = null,
    val name: String,
    val species: String? = null,
    val location: String? = null,
    val acquiredDate: String? = null,
    val avatarPhotoLocalId: Long? = null,
    /** Cached from the server's joined avatar_photo_path (ARCHITECTURE.md's
     * plants routes) so the avatar can render without a second lookup. */
    val avatarPhotoPath: String? = null,
    val generalNotes: String? = null,
    val archived: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val dirty: Boolean = false,
    val deleted: Boolean = false,
    val pendingSync: Boolean = false,
)
