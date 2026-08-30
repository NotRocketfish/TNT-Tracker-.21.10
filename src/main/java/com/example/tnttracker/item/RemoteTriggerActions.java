package com.example.tnttracker.item;

import com.example.tnttracker.ServerTaskScheduler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

public final class RemoteTriggerActions {
    private RemoteTriggerActions() {
    }

    public static boolean trigger(ServerPlayerEntity player, Hand hand, BlockPos pos, String dimensionId) {
        World world = player.getWorld();
        if (dimensionId != null && !dimensionId.equals(world.getRegistryKey().getValue().toString())) {
            return false;
        }
        if (!World.isValid(pos) || !world.isInBuildLimit(pos) || !world.canPlayerModifyAt(player, pos)) {
            return false;
        }
        if (world.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false) == null) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        if (state.isOf(Blocks.TRIPWIRE_HOOK) || state.isOf(Blocks.TRIPWIRE)) {
            ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, world);
            arrow.setPosition(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
            arrow.setNoGravity(true);
            arrow.setInvisible(true);
            arrow.setInvulnerable(true);
            if (!world.spawnEntity(arrow)) {
                return false;
            }
            ServerTaskScheduler.schedule(world, 1, arrow::discard);
            return true;
        }

        ActionResult result = player.interactionManager.interactBlock(
                player,
                world,
                player.getStackInHand(hand),
                hand,
                new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false)
        );
        return result.isAccepted();
    }
}
