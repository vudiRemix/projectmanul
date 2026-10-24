package dev.projectskins.network;

import dev.projectskins.skin.SkinSource;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Проверяет сетевой формат, ограничения входных данных и повторную валидацию URL. */
class SkinSyncPacketTest {
    @Test
    void transfersPlayerIdentitySourceAndRevisionForBothSourceKinds() {
        for (String input : new String[]{"bazuev", "https://imgur.com/Ab123.png"}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                SkinSyncPacket packet = new SkinSyncPacket(UUID.randomUUID(), SkinSource.parse(input), 42);
                SkinSyncPacket.encode(packet, buffer);
                assertEquals(packet, SkinSyncPacket.decode(buffer));
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void refusesUnknownSourceKind() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeByte(2);
            assertThrows(IllegalArgumentException.class, () -> SkinSyncPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void refusesForbiddenUrlEvenWhenItArrivesInPacket() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeByte(1);
            buffer.writeUtf("https://imgur.com.example.org/Ab123.png");
            buffer.writeVarLong(1);
            assertThrows(IllegalArgumentException.class, () -> SkinSyncPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void refusesNonpositiveRevision() {
        assertThrows(IllegalArgumentException.class,
                () -> new SkinSyncPacket(UUID.randomUUID(), SkinSource.parse("bazuev"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SkinSyncPacket(UUID.randomUUID(), SkinSource.parse("bazuev"), -1));
    }

    @Test
    void refusesOversizedStringBeforeParsingSource() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeByte(1);
            buffer.writeUtf("a".repeat(2049));
            buffer.writeVarLong(1);
            assertThrows(io.netty.handler.codec.DecoderException.class,
                    () -> SkinSyncPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void refusesTruncatedPacketInsteadOfCreatingPartialAssignment() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeUUID(UUID.randomUUID());
            buffer.writeByte(0);
            buffer.writeUtf("bazuev");
            // Номер изменения отсутствует: декодер не должен создавать назначение.
            assertThrows(IndexOutOfBoundsException.class, () -> SkinSyncPacket.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
