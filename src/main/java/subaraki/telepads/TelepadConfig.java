package subaraki.telepads;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public final class TelepadConfig {
    public static final ForgeConfigSpec SERVER, CLIENT;
    public static final ForgeConfigSpec.IntValue WAIT_SECONDS, XP_LEVELS, XP_POINTS;
    public static final ForgeConfigSpec.BooleanValue DRAGON_BLOCK, BEAD, NECKLACE, ANVIL, PARTICLES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> DESTINATIONS;
    static {
        var server = new ForgeConfigSpec.Builder();
        WAIT_SECONDS = server.comment("Seconds standing on a platform before activation.").defineInRange("waitSeconds", 3, 0, 60);
        XP_LEVELS = server.comment("Levels charged after successful platform travel; takes precedence over points.").defineInRange("xpLevels", 0, 0, 10000);
        XP_POINTS = server.defineInRange("xpPoints", 0, 0, 100000000);
        DRAGON_BLOCK = server.define("blockEndWhileDragonAlive", true);
        BEAD = server.define("enableEnderBead", true);
        NECKLACE = server.define("enableEnderBeadNecklace", true);
        ANVIL = server.define("enableAnvilConversion", true);
        DESTINATIONS = server.comment("x/y/z/dimension/name; each coordinate accepts an integer, min#max or random.")
            .defineListAllowEmpty("destinations", List.of(), value -> value instanceof String);
        SERVER = server.build();
        var client = new ForgeConfigSpec.Builder();
        PARTICLES = client.define("particles", true);
        CLIENT = client.build();
    }
    private TelepadConfig() {}
}
