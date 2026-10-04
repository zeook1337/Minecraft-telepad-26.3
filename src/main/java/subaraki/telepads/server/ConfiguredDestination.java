package subaraki.telepads.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import java.util.*;

public record ConfiguredDestination(Axis x, Axis y, Axis z, String dimension, String name) {
    public record Axis(Integer min, Integer max) {
        public static Axis parse(String value) {
            if (value.equals("random")) return new Axis(null, null);
            var pieces = value.split("#", -1);
            if (pieces.length < 1 || pieces.length > 2) throw new IllegalArgumentException("Invalid coordinate: " + value);
            int a = Integer.parseInt(pieces[0]), b = pieces.length == 1 ? a : Integer.parseInt(pieces[1]);
            if (a > b || Math.abs((long)a) > 30000000 || Math.abs((long)b) > 30000000) throw new IllegalArgumentException("Invalid coordinate interval: " + value);
            return new Axis(a, b);
        }
        public int choose(int lower, int upper, RandomSource random) {
            int a = min == null ? lower : Math.max(lower, min), b = max == null ? upper : Math.min(upper, max);
            if (a > b) throw new IllegalArgumentException("Coordinate outside destination limits");
            return a + random.nextInt(b - a + 1);
        }
    }
    public record Resolved(ServerLevel level, BlockPos pos) {}
    public static ConfiguredDestination parse(String value) {
        var parts = value.split("/", -1);
        if (parts.length != 5 || parts[4].isBlank() || parts[4].length() > 16 || parts[4].chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Expected x/y/z/dimension/name with a name of 1–16 characters");
        if (!parts[3].equals("random") && net.minecraft.resources.Identifier.tryParse(parts[3]) == null) throw new IllegalArgumentException("Invalid dimension identifier");
        return new ConfiguredDestination(Axis.parse(parts[0]), Axis.parse(parts[1]), Axis.parse(parts[2]), parts[3], parts[4]);
    }
    public Resolved resolve(MinecraftServer server, RandomSource random) {
        List<ServerLevel> levels = new ArrayList<>(); server.getAllLevels().forEach(levels::add);
        ServerLevel level = dimension.equals("random") ? levels.get(random.nextInt(levels.size())) : TravelService.dimension(server, dimension);
        if (level == null) throw new IllegalArgumentException("Unknown destination dimension: " + dimension);
        var border = level.getWorldBorder();
        int minX = Math.max(-29999984, (int)Math.ceil(border.getMinX() + 1));
        int maxX = Math.min(29999984, (int)Math.floor(border.getMaxX() - 1));
        int minZ = Math.max(-29999984, (int)Math.ceil(border.getMinZ() + 1));
        int maxZ = Math.min(29999984, (int)Math.floor(border.getMaxZ() - 1));
        int chosenX = x.choose(minX, maxX, random), chosenZ = z.choose(minZ, maxZ, random);
        int chosenY;
        if (y.min == null) {
            level.getChunk(chosenX >> 4, chosenZ >> 4);
            chosenY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, chosenX, chosenZ);
            if (chosenY < level.getMinY() || chosenY > level.getMaxY() - 2) throw new IllegalArgumentException("No surface within dimension height");
        } else chosenY = y.choose(level.getMinY(), level.getMaxY() - 2, random);
        return new Resolved(level, new BlockPos(chosenX, chosenY, chosenZ));
    }
}
