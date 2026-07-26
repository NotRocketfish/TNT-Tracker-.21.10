package com.example.tnttracker.network;

import com.example.tnttracker.ServerTaskScheduler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

public class ClientTriggerPacket {
    public static final Identifier ID = new Identifier("tnttracker", "remote_trigger");

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                World world = player.getWorld();
                BlockState state = world.getBlockState(pos);

                if (state.getBlock() == Blocks.TRIPWIRE_HOOK || state.getBlock() == Blocks.TRIPWIRE) {
                    // Spawn an invisible arrow to trigger the tripwire, then remove it after 1 tick
                    ArrowEntity arrow = new ArrowEntity(EntityType.ARROW, world);
                    arrow.setPosition(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5);
                    arrow.setNoGravity(true);
                    arrow.setInvisible(true);
                    arrow.setInvulnerable(true);
                    world.spawnEntity(arrow);

                    // Remove the arrow on the next tick
                    ServerTaskScheduler.schedule(world, 1, arrow::discard);
                } else {
                    // Normal right‑click for buttons, levers, etc.
                    player.interactionManager.interactBlock(player,
                            world,
                            player.getStackInHand(player.getActiveHand()),
                            player.getActiveHand(),
                            new BlockHitResult(
                                    Vec3d.ofCenter(pos),
                                    Direction.UP,
                                    pos,
                                    false
                            )
                    );
                }
            });
        });
    }

    public static void send(BlockPos pos) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(ID, buf);
    }
}