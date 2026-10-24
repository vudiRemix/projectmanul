package dev.projectskins.client;

import dev.projectskins.skin.SkinSource;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет порядок пакетов и жизненный цикл асинхронных запросов без игрового
 * клиента. Завершение загрузки моделируется проверкой сохранённого маркера:
 * реальные потоки и задержки для этих детерминированных сценариев не нужны.
 */
class SkinAssignmentsTest {
    private static final UUID FIRST_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final SkinSource FIRST_SKIN = SkinSource.parse("first_skin");
    private static final SkinSource SECOND_SKIN = SkinSource.parse("second_skin");

    @Test
    void delayedDownloadCannotOverwriteNewerSkin() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request slowDownload = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 1);
        SkinAssignments.Request newerDownload = assignments.accept(FIRST_PLAYER, SECOND_SKIN, 2);

        assertTrue(assignments.isCurrent(newerDownload));
        assertFalse(assignments.isCurrent(slowDownload));
        assertEquals(SECOND_SKIN, assignments.requests().iterator().next().source());
    }

    @Test
    void repeatedSourceStillInvalidatesEarlierRequest() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request old = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 1);
        SkinAssignments.Request replacement = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 2);

        assertFalse(assignments.isCurrent(old));
        assertTrue(assignments.isCurrent(replacement));
    }

    @Test
    void duplicateAndOutOfOrderPacketsPreserveLatestAssignment() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request current = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 8);

        assertNull(assignments.accept(FIRST_PLAYER, SECOND_SKIN, 8));
        assertNull(assignments.accept(FIRST_PLAYER, SECOND_SKIN, 7));
        assertTrue(assignments.isCurrent(current));
        assertEquals(1, assignments.requests().size());
    }

    @Test
    void changingServersAllowsRevisionOneAndInvalidatesOldDownload() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request previousServer = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 1);

        assignments.reset();
        assertTrue(assignments.requests().isEmpty());
        assertFalse(assignments.isCurrent(previousServer));

        SkinAssignments.Request nextServer = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 1);
        assertNotNull(nextServer);
        assertNotEquals(previousServer.session(), nextServer.session());
        assertFalse(assignments.isCurrent(previousServer));
        assertTrue(assignments.isCurrent(nextServer));
    }

    @Test
    void playerRevisionsAndRequestsAreIndependent() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request first = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 100);
        SkinAssignments.Request second = assignments.accept(SECOND_PLAYER, FIRST_SKIN, 1);
        SkinAssignments.Request firstUpdated = assignments.accept(FIRST_PLAYER, SECOND_SKIN, 101);

        assertFalse(assignments.isCurrent(first));
        assertTrue(assignments.isCurrent(firstUpdated));
        assertTrue(assignments.isCurrent(second));
        assertEquals(2, assignments.requests().size());
    }

    @Test
    void nonpositiveRevisionsAreRejectedWithoutReplacingCurrentSkin() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request current = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 1);

        for (long revision : new long[]{0, -1, Long.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> assignments.accept(FIRST_PLAYER, SECOND_SKIN, revision));
        }
        assertTrue(assignments.isCurrent(current));
    }

    @Test
    void snapshotIsImmutableAndUnaffectedByLaterUpdates() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request first = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 1);
        Collection<SkinAssignments.Request> snapshot = assignments.requests();

        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assignments.accept(SECOND_PLAYER, SECOND_SKIN, 1);
        assignments.reset();

        assertEquals(1, snapshot.size());
        assertSame(first, snapshot.iterator().next());
        assertTrue(assignments.requests().isEmpty());
    }

    @Test
    void nullAndReconstructedMarkersAreNotCurrentRequests() {
        SkinAssignments assignments = new SkinAssignments();
        SkinAssignments.Request current = assignments.accept(FIRST_PLAYER, FIRST_SKIN, 1);
        SkinAssignments.Request copied = new SkinAssignments.Request(
                current.session(), current.playerId(), current.source(), current.revision());

        assertFalse(assignments.isCurrent(null));
        assertFalse(assignments.isCurrent(copied));
        assertTrue(assignments.isCurrent(current));
    }
}
