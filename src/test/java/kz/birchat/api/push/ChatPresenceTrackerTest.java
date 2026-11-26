package kz.birchat.api.push;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatPresenceTrackerTest {

    private final ChatPresenceTracker tracker = new ChatPresenceTracker();
    private final UUID user = UUID.randomUUID();
    private final UUID chat = UUID.randomUUID();
    private final UUID company = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void userWhoPolledRecentlyIsInChat() {
        tracker.touch(user + ":" + chat, now.minusSeconds(14));

        assertTrue(tracker.isInChat(user, chat, null, now));
    }

    @Test
    void userWhoStoppedPollingIsNotInChat() {
        tracker.touch(user + ":" + chat, now.minusSeconds(15));

        assertFalse(tracker.isInChat(user, chat, null, now));
        assertFalse(tracker.isInChat(UUID.randomUUID(), chat, null, now));
    }

    @Test
    void generalChatOpenedByOldAddressCounts() {
        tracker.touch(user + ":general:" + company, now.minusSeconds(3));

        assertTrue(tracker.isInChat(user, chat, company, now));
        assertFalse(tracker.isInChat(user, chat, null, now)); // группа той же компании — не считается
    }

    @Test
    void leaveForgetsAllChatsOfUserOnly() {
        ChatPresenceTracker tracker = new ChatPresenceTracker();
        java.util.UUID me = java.util.UUID.randomUUID();
        java.util.UUID other = java.util.UUID.randomUUID();
        java.util.UUID chat = java.util.UUID.randomUUID();
        java.util.UUID company = java.util.UUID.randomUUID();
        tracker.touchChat(me, chat);
        tracker.touchGeneralChat(me, company);
        tracker.touchChat(other, chat);

        org.junit.jupiter.api.Assertions.assertEquals(0L, tracker.secondsSinceSeen(me, chat, null));

        tracker.leave(me);

        org.junit.jupiter.api.Assertions.assertFalse(tracker.isInChat(me, chat, company));
        org.junit.jupiter.api.Assertions.assertNull(tracker.secondsSinceSeen(me, chat, company));
        org.junit.jupiter.api.Assertions.assertTrue(tracker.isInChat(other, chat, null));
    }
}
