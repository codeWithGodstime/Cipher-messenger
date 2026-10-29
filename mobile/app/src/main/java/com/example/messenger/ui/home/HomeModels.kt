package com.example.messenger.ui.home

import java.util.Calendar

/**
 * Local stand-ins for the chat list, conversation, and new-chat screens.
 * The API stores a profile only, so these threads are the Stitch sample
 * until conversations exist on the server.
 */

enum class HomeTab {
    Chats,
    Calls,
    Settings,
}

enum class AvatarTone {
    Cobalt,
    Teal,
    Clay,
    Mist,
    Sea,
    Neutral,
    Quiet,
}

enum class Receipt {
    Sent,
    Read,
}

enum class PinnedMark {
    Lock,
    Read,
}

data class ChatThread(
    val id: String,
    val title: String,
    val initials: String,
    val tone: AvatarTone,
    val snippet: String,
    val timeLabel: String,
    val presence: String,
    val online: Boolean = false,
    val unread: Int = 0,
    val receipt: Receipt? = null,
    val voice: Boolean = false,
    val pinned: Boolean = false,
    val inRecent: Boolean = true,
    val group: Boolean = false,
    val pinnedMark: PinnedMark? = null,
)

data class CipherMessage(
    val id: String,
    val outgoing: Boolean,
    val body: String,
    val time: String,
    val read: Boolean = false,
)

data class ContactEntry(
    val id: String,
    val name: String,
    val initials: String,
    val tone: AvatarTone,
    val detail: String,
    val online: Boolean,
    val encrypted: Boolean,
    val group: Boolean = false,
)

fun initialsFor(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

fun clockLabel(): String {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR)
    val displayHour = if (hour == 0) 12 else hour
    val minute = calendar.get(Calendar.MINUTE)
    val suffix = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    return "%d:%02d %s".format(displayHour, minute, suffix)
}

fun ContactEntry.asThread(): ChatThread = ChatThread(
    id = id,
    title = name,
    initials = initials,
    tone = tone,
    snippet = "End-to-end encrypted",
    timeLabel = "Now",
    presence = if (online) "online" else detail,
    online = online,
    group = group,
)

object CipherSamples {
    val threads = listOf(
        ChatThread(
            id = "design-team",
            title = "Design Team",
            initials = "DT",
            tone = AvatarTone.Cobalt,
            snippet = "Alex: Meeting at 3pm",
            timeLabel = "Yesterday",
            presence = "8 members",
            pinned = true,
            inRecent = false,
            group = true,
            pinnedMark = PinnedMark.Lock,
        ),
        ChatThread(
            id = "elena",
            title = "Elena Rostova",
            initials = "ER",
            tone = AvatarTone.Neutral,
            snippet = "Looks great! Let's ship it 🚀",
            timeLabel = "10:42 AM",
            presence = "online",
            online = true,
            receipt = Receipt.Read,
            pinned = true,
            pinnedMark = PinnedMark.Read,
        ),
        ChatThread(
            id = "marcus",
            title = "Marcus Vance",
            initials = "MV",
            tone = AvatarTone.Mist,
            snippet = "Security audit report is ready for review",
            timeLabel = "9:15 AM",
            presence = "Last seen 2h ago",
            unread = 2,
        ),
        ChatThread(
            id = "sarah",
            title = "Sarah Jenkins",
            initials = "SJ",
            tone = AvatarTone.Clay,
            snippet = "Voice note (0:45)",
            timeLabel = "Yesterday",
            presence = "online",
            online = true,
            receipt = Receipt.Sent,
            voice = true,
        ),
        ChatThread(
            id = "tech-hub",
            title = "Tech Hub Group",
            initials = "TH",
            tone = AvatarTone.Sea,
            snippet = "Alex: Meeting at 3pm",
            timeLabel = "Yesterday",
            presence = "142 members",
            receipt = Receipt.Read,
            group = true,
        ),
        ChatThread(
            id = "david",
            title = "David Kim",
            initials = "DK",
            tone = AvatarTone.Quiet,
            snippet = "Key exchange verified successfully 🔐",
            timeLabel = "Oct 24",
            presence = "online",
            online = true,
            receipt = Receipt.Read,
        ),
    )

    val messages: Map<String, List<CipherMessage>> = mapOf(
        "elena" to listOf(
            CipherMessage(
                id = "elena-1",
                outgoing = false,
                body = "Hi Alex! Have you had a chance to look at the new encryption protocol specs?",
                time = "10:35 AM",
            ),
            CipherMessage(
                id = "elena-2",
                outgoing = true,
                body = "Yes! Reviewing them now. The zero-knowledge proof implementation looks very robust.",
                time = "10:38 AM",
                read = true,
            ),
            CipherMessage(
                id = "elena-3",
                outgoing = false,
                body = "Looks great! Let's ship it 🚀",
                time = "10:42 AM",
            ),
        ),
        "marcus" to listOf(
            CipherMessage(
                id = "marcus-1",
                outgoing = false,
                body = "Security audit report is ready for review",
                time = "9:15 AM",
            ),
        ),
        "sarah" to listOf(
            CipherMessage(
                id = "sarah-1",
                outgoing = false,
                body = "Voice note (0:45)",
                time = "Yesterday",
            ),
        ),
        "tech-hub" to listOf(
            CipherMessage(
                id = "hub-1",
                outgoing = false,
                body = "Alex: Meeting at 3pm",
                time = "Yesterday",
            ),
        ),
        "david" to listOf(
            CipherMessage(
                id = "david-1",
                outgoing = false,
                body = "Key exchange verified successfully 🔐",
                time = "Oct 24",
            ),
        ),
        "design-team" to listOf(
            CipherMessage(
                id = "design-1",
                outgoing = false,
                body = "Alex: Meeting at 3pm",
                time = "Yesterday",
            ),
        ),
    )

    val frequent = listOf(
        contact("elena", "Online • End-to-end encrypted", online = true),
        contact("marcus", "Last seen 2h ago", online = false),
        contact("sarah", "Online • End-to-end encrypted", online = true),
    )

    val groups = listOf(
        ContactEntry(
            id = "tech-hub",
            name = "Tech Hub Group",
            initials = "TH",
            tone = AvatarTone.Mist,
            detail = "142 members • 3 online",
            online = false,
            encrypted = true,
            group = true,
        ),
    )

    val directory = listOf(
        contact("david", "Online • End-to-end encrypted", online = true),
    )

    private fun contact(id: String, detail: String, online: Boolean): ContactEntry {
        val thread = threads.first { it.id == id }
        return ContactEntry(
            id = thread.id,
            name = thread.title,
            initials = thread.initials,
            tone = thread.tone,
            detail = detail,
            online = online,
            encrypted = online,
        )
    }
}
