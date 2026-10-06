package subaraki.telepads.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.Optional;

public final class SafeArrival {
    private SafeArrival() {}
    /** At most 441 positions in a 7x7x9 neighborhood; chunk loads create no persistent tickets. */
    public static Optional<Vec3> find(ServerLevel level, ServerPlayer player, BlockPos anchor) {
        for (int radius = 0; radius <= 3; radius++) {
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4}) {
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    var pos = anchor.offset(dx, dy, dz);
                    if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() - 2 || !level.getWorldBorder().isWithinBounds(pos)) continue;
                    level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
                    var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    double y = pos.getY() + (shape.isEmpty() ? 0 : shape.max(net.minecraft.core.Direction.Axis.Y));
                    var feet = new Vec3(pos.getX() + 0.5, y, pos.getZ() + 0.5);
                    AABB body = player.getDimensions(Pose.STANDING).makeBoundingBox(feet);
                    AABB support = new AABB(body.minX + 0.05, y - 0.06, body.minZ + 0.05, body.maxX - 0.05, y, body.maxZ - 0.05);
                    if (body.maxY <= level.getMaxY() + 1 && level.getWorldBorder().isWithinBounds(body)
                        && level.noCollision(player, body) && !level.containsAnyLiquid(body)
                        && !level.noCollision(null, support) && !hazardous(level, body, support)) return Optional.of(feet);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean hazardous(ServerLevel level, AABB body, AABB support) {
        // Include the thin contact slice, rather than rounding the feet down a full
        // block: a 0.2-high telepad can safely separate feet from magma beneath it.
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(body.minX, support.minY, body.minZ),
                BlockPos.containing(Math.nextDown(body.maxX), Math.nextDown(body.maxY), Math.nextDown(body.maxZ)))) {
            if (hazard(level.getBlockState(pos))) return true;
        }
        return false;
    }

    private static boolean hazard(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)
            || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
            || ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(BlockStateProperties.LIT));
    }
}
