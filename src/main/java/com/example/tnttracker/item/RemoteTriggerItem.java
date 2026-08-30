package com.example.tnttracker.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RemoteTriggerItem extends Item {

    public RemoteTriggerItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        if (!player.isSneaking()) return ActionResult.PASS;

        World world = context.getWorld();
        ItemStack stack = context.getStack();
        BlockPos pos = context.getBlockPos();

        if (world.isClient) return ActionResult.SUCCESS;

        saveBoundPos(stack, pos, world);
        player.sendMessage(
                Text.translatable("tnttracker.item.remote_trigger.bound", pos.toShortString())
                        .formatted(Formatting.GREEN),
                true
        );
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        HitResult hit = player.raycast(5.0, 0.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }

        if (world.isClient) return TypedActionResult.success(stack);

        BlockPos bound = getBoundPos(stack);
        if (bound == null) {
            player.sendMessage(
                    Text.translatable("tnttracker.item.remote_trigger.no_bound")
                            .formatted(Formatting.RED),
                    true
            );
            return TypedActionResult.fail(stack);
        }

        if (player instanceof ServerPlayerEntity serverPlayer &&
                RemoteTriggerActions.trigger(serverPlayer, hand, bound, getBoundDimension(stack))) {
            player.sendMessage(
                    Text.translatable("tnttracker.item.remote_trigger.triggered", bound.toShortString())
                            .formatted(Formatting.GREEN),
                    true
            );
            return TypedActionResult.success(stack);
        }

        player.sendMessage(
                Text.translatable("tnttracker.item.remote_trigger.no_bound")
                        .formatted(Formatting.RED),
                true
        );
        return TypedActionResult.fail(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        BlockPos bound = getBoundPos(stack);
        if (bound != null) {
            tooltip.add(
                    Text.translatable("tnttracker.item.remote_trigger.bound_tooltip", bound.toShortString())
                            .formatted(Formatting.GRAY)
            );
        } else {
            tooltip.add(
                    Text.translatable("tnttracker.item.remote_trigger.tooltip_bind")
                            .formatted(Formatting.GRAY)
            );
            tooltip.add(
                    Text.translatable("tnttracker.item.remote_trigger.tooltip_trigger")
                            .formatted(Formatting.GRAY)
            );
        }
    }

    private static void saveBoundPos(ItemStack stack, BlockPos pos, World world) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putInt("boundX", pos.getX());
        nbt.putInt("boundY", pos.getY());
        nbt.putInt("boundZ", pos.getZ());
        nbt.putString("boundDimension", world.getRegistryKey().getValue().toString());
        stack.setNbt(nbt);
    }

    public static BlockPos getBoundPos(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasNbt()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("boundX") || !nbt.contains("boundY") || !nbt.contains("boundZ")) return null;
        return new BlockPos(
                nbt.getInt("boundX"),
                nbt.getInt("boundY"),
                nbt.getInt("boundZ")
        );
    }

    public static String getBoundDimension(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasNbt()) return null;
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains("boundDimension")) return null;
        return nbt.getString("boundDimension");
    }
}
