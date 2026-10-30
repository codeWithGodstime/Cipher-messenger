package com.example.messenger.data.chat

import com.example.messenger.data.api.ApiContact
import com.example.messenger.data.api.ApiConversation
import com.example.messenger.data.api.ApiMessage
import com.example.messenger.ui.home.AvatarTone
import com.example.messenger.ui.home.ChatThread
import com.example.messenger.ui.home.CipherMessage
import com.example.messenger.ui.home.ContactEntry
import com.example.messenger.ui.home.Receipt
import com.example.messenger.ui.home.initialsFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun ApiConversation.toThread(currentUserId: String): ChatThread {
    val title = peer.displayName.ifBlank { peer.phone }.ifBlank { "Chat" }
    val last = lastMessage
    return ChatThread(
        id = id,
        peerId = peer.id,
        title = title,
        initials = initialsFor(title),
        tone = toneFor(peer.id),
        snippet = last?.body?.ifBlank { null } ?: "End-to-end encrypted",
        timeLabel = formatChatTime(last?.createdAt ?: updatedAt),
        presence = peer.about.ifBlank { peer.phone }.ifBlank { "Tap to chat" },
        unread = unread,
        receipt = when {
            last == null -> null
            last.senderId == currentUserId -> Receipt.Sent
            else -> null
        },
        inRecent = true,
    )
}

fun ApiMessage.toCipherMessage(currentUserId: String): CipherMessage {
    val outgoing = senderId == currentUserId
    return CipherMessage(
        id = id,
        outgoing = outgoing,
        body = body,
        time = formatChatTime(createdAt),
        read = outgoing,
    )
}

fun ChatThread.asContact(): ContactEntry = ContactEntry(
    id = peerId.ifBlank { id },
    name = title,
    initials = initials,
    tone = tone,
    detail = presence,
    online = online,
    encrypted = true,
    group = group,
)

fun ApiContact.asContactEntry(): ContactEntry {
    val name = peer.displayName.ifBlank { peer.phone }.ifBlank { "Contact" }
    return ContactEntry(
        id = peer.id,
        name = name,
        initials = initialsFor(name),
        tone = toneFor(peer.id),
        detail = peer.phone.ifBlank { peer.about }.ifBlank { "Tap to chat" },
        online = false,
        encrypted = true,
        contactId = id,
    )
}

fun toneFor(seed: String): AvatarTone {
    val tones = AvatarTone.entries
    if (seed.isEmpty()) return AvatarTone.Neutral
    val index = seed.fold(0) { acc, ch -> acc * 31 + ch.code }.mod(tones.size)
    return tones[index]
}

fun formatChatTime(iso: String): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val zone = ZoneId.systemDefault()
    val zoned = instant.atZone(zone)
    val today = LocalDate.now(zone)
    val date = zoned.toLocalDate()
    return when {
        date == today -> {
            val hour24 = zoned.hour
            val hour = when (val mod = hour24 % 12) {
                0 -> 12
                else -> mod
            }
            val minute = zoned.minute
            val suffix = if (hour24 < 12) "AM" else "PM"
            "%d:%02d %s".format(hour, minute, suffix)
        }
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> zoned.format(MONTH_DAY)
        else -> zoned.format(MONTH_DAY_YEAR)
    }
}

private val MONTH_DAY: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)

private val MONTH_DAY_YEAR: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
