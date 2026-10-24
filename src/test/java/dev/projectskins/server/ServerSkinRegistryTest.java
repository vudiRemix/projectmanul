package dev.projectskins.server;

import dev.projectskins.network.SkinSyncPacket;
import dev.projectskins.skin.SkinSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверяет инварианты полного снимка и порядка назначений при повторных командах. */
class ServerSkinRegistryTest {
    @Test
    void newerCommandReplacesOnlyItsPlayerAndAdvancesRevision() {
        ServerSkinRegistry registry = new ServerSkinRegistry();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        SkinSyncPacket first = registry.assign(firstPlayer, SkinSource.parse("first"));
        SkinSyncPacket second = registry.assign(secondPlayer, SkinSource.parse("second"));
        SkinSyncPacket replacement = registry.assign(firstPlayer, SkinSource.parse("replacement"));

        assertTrue(first.revision() < second.revision());
        assertTrue(second.revision() < replacement.revision());
        assertEquals(2, registry.snapshot().size());
        assertTrue(registry.snapshot().contains(second));
        assertTrue(registry.snapshot().contains(replacement));
    }

    @Test
    void capturedSnapshotCannotChangeWhenAnotherCommandRuns() {
        ServerSkinRegistry registry = new ServerSkinRegistry();
        UUID player = UUID.randomUUID();
        SkinSyncPacket original = registry.assign(player, SkinSource.parse("first"));
        List<SkinSyncPacket> snapshot = registry.snapshot();
        registry.assign(player, SkinSource.parse("second"));

        assertEquals(List.of(original), snapshot);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    void repeatedSameSourceStillGetsNewRevisionForRetry() {
        ServerSkinRegistry registry = new ServerSkinRegistry();
        UUID player = UUID.randomUUID();
        SkinSource source = SkinSource.parse("https://i.imgur.com/Ab123.png");
        SkinSyncPacket first = registry.assign(player, source);
        SkinSyncPacket retry = registry.assign(player, source);
        assertTrue(retry.revision() > first.revision());
        assertEquals(List.of(retry), registry.snapshot());
    }

    @Test
    void stoppingServerRemovesAssignmentsBeforeNextWorld() {
        ServerSkinRegistry registry = new ServerSkinRegistry();
        registry.assign(UUID.randomUUID(), SkinSource.parse("first"));
        registry.clear();
        assertTrue(registry.snapshot().isEmpty());
    }
}
