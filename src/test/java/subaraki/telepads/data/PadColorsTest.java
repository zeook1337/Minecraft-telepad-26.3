package subaraki.telepads.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class PadColorsTest {
    private static final List<Integer> RING = List.of(14, 14, 14, 14, 11, 11, 11, 11);
    @Test void legacyAndAllPaletteStatesRoundTrip() {
        assertEquals(new PadColors(14, 11), PadColors.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"frame\":14,\"base\":11}")).getOrThrow());
        assertEquals(PadColors.DEFAULT, PadColors.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"frame\":-1,\"base\":-1}")).getOrThrow());
        var crafted = PadColors.crafted(0x9c467e, RING);
        for (var value : List.of(PadColors.DEFAULT, new PadColors(14, 11), crafted, crafted.dyed(5))) {
            var json = PadColors.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
            assertEquals(value, PadColors.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
            try { PadColors.STREAM.encode(buf, value); assertEquals(value, PadColors.STREAM.decode(buf)); assertEquals(0, buf.readableBytes()); }
            finally { buf.release(); }
        }
    }
    @Test void rejectsInvalidPersistedFieldsAndCombinations() {
        for (String json : List.of(
            "{\"frame\":16,\"base\":-1}", "{\"frame\":-2,\"base\":-1}",
            "{\"frame\":-1,\"base\":-1,\"frame_rgb\":-1}", "{\"frame\":-1,\"base\":-1,\"base_rgb\":16777216}",
            "{\"frame\":-1,\"base\":-1,\"frame_rgb\":123}",
            "{\"frame\":-1,\"base\":-1,\"craft_dyes\":[1,1,1,1,1,1,1,1]}",
            "{\"frame\":-1,\"base\":-1,\"frame_rgb\":123,\"craft_dyes\":[1]}",
            "{\"frame\":-1,\"base\":-1,\"frame_rgb\":123,\"craft_dyes\":[1,1,1,1,1,1,1,16]}",
            "{\"frame\":-1,\"base\":-1,\"frame_rgb\":123,\"craft_dyes\":[1,1,1,1,1,1,1,1,1]}",
            "{\"frame\":3,\"base\":-1,\"frame_rgb\":123,\"craft_dyes\":[1,1,1,1,1,1,1,1]}",
            "{\"frame\":-1,\"base\":-1,\"frame_rgb\":123,\"base_rgb\":124,\"craft_dyes\":[1,1,1,1,1,1,1,1]}")) {
            assertTrue(PadColors.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
        }
        assertThrows(IllegalArgumentException.class, () -> new PadColors(-1, 16));
        assertThrows(IllegalArgumentException.class, () -> PadColors.crafted(-1, RING));
        assertThrows(IllegalArgumentException.class, () -> PadColors.crafted(0x1000000, RING));
        assertThrows(IllegalArgumentException.class, () -> PadColors.crafted(1, List.of(-1, 0, 0, 0, 0, 0, 0, 0)));
    }
    @Test void receiptIsImmutableAndLatestCraftOnly() {
        var mutable = new ArrayList<>(RING); var value = PadColors.crafted(0x123456, mutable); mutable.clear();
        assertEquals(RING, value.craftDyes());
        assertThrows(UnsupportedOperationException.class, () -> value.craftDyes().add(1));
        assertEquals(0xff123456, value.color(0)); assertEquals(value.color(0), value.color(1));
        var first = value.dyed(5);
        assertEquals(5, first.frame()); assertEquals(Optional.empty(), first.frameRgb());
        assertEquals(value.baseRgb(), first.baseRgb()); assertEquals(9, first.recoveredDyes().size());
        var second = first.dyed(2);
        assertEquals(new PadColors(5, 2), second); assertEquals(List.of(5, 2), second.recoveredDyes());
        assertEquals(new PadColors(5, 3), second.dyed(3));
        var recrafted = PadColors.crafted(0xabcdef, Collections.nCopies(8, 7));
        assertEquals(Collections.nCopies(8, 7), recrafted.recoveredDyes()); assertEquals(-1, recrafted.frame());
        assertEquals(0xffb8c4cb, PadColors.DEFAULT.color(0)); assertEquals(0xff18cbd1, PadColors.DEFAULT.color(1));
        assertEquals(new PadColors(1, 2), PadColors.DEFAULT.dyed(1).dyed(2));
    }
    @Test void rejectsMalformedNetworkReceiptBeforeReadingUnboundedData() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buf.writeByte(-1); buf.writeByte(-1); buf.writeBoolean(false); buf.writeBoolean(false); buf.writeByte(255);
            assertThrows(IllegalArgumentException.class, () -> PadColors.STREAM.decode(buf));
            buf.clear(); buf.writeByte(16); buf.writeByte(-1); buf.writeBoolean(false); buf.writeBoolean(false); buf.writeByte(0);
            assertThrows(IllegalArgumentException.class, () -> PadColors.STREAM.decode(buf));
        } finally { buf.release(); }
    }
}
