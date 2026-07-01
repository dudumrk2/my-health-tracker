package com.myhealthtracker.app.data.reminders

import java.time.LocalTime

/** One meal-time reminder. `time` is the moment the popup fires; `mealLabel` is the Hebrew meal name. */
data class ReminderSlot(
    val time: LocalTime,
    val mealLabel: String,
    val enabled: Boolean = true
)

/** All reminder settings. Device-local only; never synced to Firestore. */
data class ReminderSettings(
    val masterEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val slots: List<ReminderSlot>
) {
    companion object {
        val DEFAULT = ReminderSettings(
            masterEnabled = true,
            soundEnabled = true,
            slots = listOf(
                ReminderSlot(LocalTime.of(7, 0), "ארוחת בוקר", true),
                ReminderSlot(LocalTime.of(12, 0), "ארוחת צהריים", true),
                ReminderSlot(LocalTime.of(19, 0), "ארוחת ערב", true),
            )
        )
    }
}

/** Serializes slots to a single ordered string for DataStore (labels never contain '|' or ';'). */
object ReminderSettingsCodec {
    fun encodeSlots(slots: List<ReminderSlot>): String =
        slots.joinToString(";") { "${it.time}|${it.mealLabel}|${it.enabled}" }

    fun decodeSlots(encoded: String): List<ReminderSlot> {
        if (encoded.isBlank()) return emptyList()
        return runCatching {
            encoded.split(";").map { part ->
                val (t, label, en) = part.split("|")
                ReminderSlot(LocalTime.parse(t), label, en.toBoolean())
            }
        }.getOrElse { ReminderSettings.DEFAULT.slots }
    }
}
