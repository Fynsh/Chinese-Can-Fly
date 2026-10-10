package com.Fynsh.ccf;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class CCFBlockHandler {

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!CCFMod.followEnabled) return;

        Player player = event.getPlayer();
        if (player == null) return;

        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof ServerLevel serverLevel)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();

        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null, player, player.getMainHandItem());
        if (drops.isEmpty()) return;

        event.setCanceled(true);
        serverLevel.destroyBlock(pos, false);

        for (ItemStack drop : drops) {
            if (!player.getInventory().add(drop)) {
                spawnFollowItem(serverLevel, drop, player);
            }
        }
    }

    private static void spawnFollowItem(ServerLevel level, ItemStack stack, Player player) {
        // 直接生成在玩家脚边（脚部高度略微上抬 0.1，避免与玩家碰撞箱重叠）
        ItemEntity itemEntity = new ItemEntity(
                level,
                player.getX(),
                player.getY() + 0.1,
                player.getZ(),
                stack
        );

        // 关闭重力：让它完全跟随玩家，不受地形与抛物线影响
        itemEntity.setNoGravity(true);

        // 允许立即被拾取（背包一旦腾出空位就会自动收进背包）
        itemEntity.setPickUpDelay(0);

        itemEntity.getPersistentData().putBoolean("CCF_Following", true);
        itemEntity.getPersistentData().putInt("CCF_FollowTicks", CCFConfig.getFollowingTime());

        level.addFreshEntity(itemEntity);
    }
}