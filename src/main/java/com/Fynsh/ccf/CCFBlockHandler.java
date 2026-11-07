package com.Fynsh.ccf;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 把玩家挖掘方块时产生的掉落物直接送入背包；背包已满时转为“跟随掉落物”。
 *
 * <p>实现上不取消 {@link BlockEvent.BreakEvent}，而是让原版完整执行破坏流程，
 * 因此工具耐久消耗、方块经验、统计/进度、{@code Block#playerDestroy}、容器内容物
 * 等行为都与原版一致。随后在 {@link EntityJoinLevelEvent} 中拦截本次破坏在方块位置
 * 附近生成的 {@link ItemEntity}，把物品转运进背包并取消该实体加入世界。</p>
 *
 * <p>这样既保留了原版语义，也避免了过去“取消事件 + 手动 destroyBlock”导致的
 * 工具不掉耐久、不给经验、潜影盒内容物丢失、创造模式仍产出掉落物等问题。</p>
 */
@Mod.EventBusSubscriber(modid = CCFMod.MODID, value = Dist.CLIENT)
public final class CCFBlockHandler {

    /** 判定“这是该方块产出的掉落物”的水平/竖直半径（方块）。 */
    private static final double CAPTURE_RADIUS = 1.5D;

    /** 最近发生的破坏记录，同一服务端 tick 内有效，用于匹配随后生成的掉落物。 */
    private static final List<DropCapture> CAPTURES = new ArrayList<>();

    /** 生成跟随掉落物时置位，避免其加入世界时再次被本处理器拦截。 */
    private static boolean spawningFollowItem;

    private CCFBlockHandler() {
    }

    /**
     * 一次玩家破坏方块时产生的掉落物生成上下文。
     *
     * @param level  发生破坏的维度
     * @param pos    被破坏方块的位置
     * @param player 破坏方块的玩家
     */
    private record DropCapture(ServerLevel level, BlockPos pos, Player player) {
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!CCFMod.followEnabled) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Player player = event.getPlayer();
        // 创造/旁观模式不接管掉落物，保持原版行为（bug 3）。
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        CAPTURES.add(new DropCapture(level, event.getPos().immutable(), player));
    }

    /**
     * 掉落物都在本次服务端 tick 内同步生成，tick 结束时清空记录即可，
     * 无需额外的定时清理逻辑。
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            CAPTURES.clear();
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!CCFMod.followEnabled || spawningFollowItem || event.isCanceled() || event.loadedFromDisk()) {
            return;
        }
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getEntity() instanceof ItemEntity item) || item.getItem().isEmpty()) {
            return;
        }
        // 跟随掉落物本身不参与捕获，避免递归。
        if (item.getPersistentData().getBoolean(CCFFollowHandler.TAG_FOLLOWING)) {
            return;
        }

        Player player = findBreakingPlayer(level, item);
        if (player == null) {
            return;
        }

        event.setCanceled(true);
        deliver(level, player, item.getItem());
    }

    /**
     * 查找本次掉落物对应的破坏者。只匹配同一维度、且在
     * {@link #CAPTURE_RADIUS} 范围内的破坏记录。
     */
    private static Player findBreakingPlayer(ServerLevel level, ItemEntity item) {
        for (DropCapture capture : CAPTURES) {
            if (capture.level() != level) {
                continue;
            }
            BlockPos pos = capture.pos();
            double dx = pos.getX() + 0.5D - item.getX();
            double dy = pos.getY() + 0.5D - item.getY();
            double dz = pos.getZ() + 0.5D - item.getZ();
            if (dx * dx + dy * dy + dz * dz <= CAPTURE_RADIUS * CAPTURE_RADIUS) {
                return capture.player();
            }
        }
        return null;
    }

    /**
     * 把掉落物放入背包；放不下的部分转为跟随掉落物。
     *
     * <p>{@link net.minecraft.world.entity.player.Inventory#add(ItemStack)} 会直接修改传入的
     * {@link ItemStack}：全部放入时返回 {@code true} 并清空，部分放入时返回 {@code false}
     * 并保留剩余数量。这里传入实体自己的物品栈即可，无需额外拷贝。</p>
     */
    private static void deliver(ServerLevel level, Player player, ItemStack stack) {
        if (player.getInventory().add(stack)) {
            return;
        }
        if (!stack.isEmpty()) {
            spawnFollowItem(level, player, stack);
        }
    }

    private static void spawnFollowItem(ServerLevel level, Player player, ItemStack stack) {
        ItemEntity item = new ItemEntity(
                level,
                player.getX(),
                player.getY() + CCFFollowHandler.TARGET_Y_OFFSET,
                player.getZ(),
                stack
        );
        item.setNoGravity(true);
        item.setPickUpDelay(0);
        item.getPersistentData().putBoolean(CCFFollowHandler.TAG_FOLLOWING, true);
        item.getPersistentData().putInt(CCFFollowHandler.TAG_FOLLOW_TICKS, CCFConfig.getFollowingTime());

        spawningFollowItem = true;
        try {
            level.addFreshEntity(item);
        } finally {
            spawningFollowItem = false;
        }
    }
}
