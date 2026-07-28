package com.lunentous.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** The set of local entities that can have unsynced writes queued. Every
 * write-capable repository implements OutboxHandler for exactly its own
 * type -- see the Android plan's Outbox design. */
enum class OutboxEntityType { PLANT, REMINDER_TYPE, PHASE_TYPE, REMINDER_RULE, PHASE_WINDOW, TIMELINE_EVENT, ONE_TIME_REMINDER }

/** Not every entity supports every op -- Plant/ReminderType/PhaseType are
 * archive-only (no server DELETE), ReminderRule/PhaseWindow/TimelineEvent
 * are delete-only (no archive), matching LunentousApi's actual endpoints.
 * APPEND_PHOTOS is TIMELINE_EVENT-only, for photos captured onto an
 * already-existing entry (see TimelineRepository.appendPhotos) --
 * independent of that event's own CREATE/UPDATE/DELETE lifecycle. */
enum class OutboxOpType { CREATE, UPDATE, DELETE, ARCHIVE, UNARCHIVE, APPEND_PHOTOS }

enum class OutboxStatus { PENDING, IN_FLIGHT, FAILED }

/**
 * Single serial FIFO queue -- `id` (insertion order) is also processing
 * order, which is what makes lazy serverId resolution safe: a child op
 * (e.g. a reminder rule referencing a reminder type) can never be
 * dequeued before its parent's own CREATE has already resolved one way or
 * another. See the Android plan's "Outbox -- single serial FIFO queue".
 *
 * `payloadJson` is Gson-serialized by the entity's own repository (which
 * knows its own payload shape) and deserialized the same way when
 * processed -- never inspected generically by OutboxProcessor.
 */
@Entity(tableName = "outbox_operations")
data class OutboxOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: OutboxEntityType,
    val entityLocalId: Long,
    val opType: OutboxOpType,
    val payloadJson: String,
    val status: OutboxStatus = OutboxStatus.PENDING,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
