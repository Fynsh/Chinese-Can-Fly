package com.Fynsh.ccf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.function.Predicate;

/**
 * 掉落物跟随行为处理器。
 *
 * <p>对带有跟随附件的 {@link ItemEntity} 施加“位置伺服”式跟随：
 * 每 tick 计算物品到玩家脚边的向量，用与距离成正比的速度朝目标推进。
 * 距离越近速度越小，接触玩家时速度归零，物品最终停在玩家脚边。</p>
 *
 * <p>关闭重力（{@code noGravity}）使物品能够穿过方块紧贴玩家，不受地形与抛物线影响。
 * 每 tick 标记 {@code hurtMarked} 以强制服务端同步位置到客户端，避免客户端自身的
 * 物理预测覆盖服务端轨迹。</p>
 *
 * <p>当掉落物功能被关闭、或跟随时间耗尽时，通过 {@link #releaseFollowItem} 释放附件，
 * 恢复物品的正常掉落物物理。</p>
 */
@EventBusSubscriber(modid = CCFMod.MODID, value = Dist.CLIENT)
public final class CCFFollowHandler {

    /** 附件键：标记该掉落物处于跟随状态。 */
    static final String TAG_FOLLOWING = "following";

    /** 附件键：剩余跟随时间（tick）。 */
    static final String TAG_FOLLOW_TICKS = "ticks";

    /** 目标点相对玩家脚部的竖直偏移，略高以避免与玩家碰撞箱重叠。 */
    static final double TARGET_Y_OFFSET = 0.1D;

    /** 速度 = 距离 × 此系数。距离越近速度越小，接触时为 0。 */
    private static final double SPEED_FACTOR = 0.4D;

    /** 速度上限，避免距离过远时单 tick 位移过大造成视觉上的瞬移。 */
    private static final double MAX_SPEED = 1.2D;

    /** 到达该距离以内直接吸附到目标点，消除线性衰减带来的渐近残留。 */
    private static final double SNAP_DISTANCE = 0.05D;

    /** 只处理带跟随附件的掉落物，避免对普通掉落物做无用判断。 */
    private static final Predicate<ItemEntity> FOLLOWING_FILTER = item ->
            item.hasData(CCFAttachments.FOLLOW_DATA)
                    && item.getExistingData(CCFAttachments.FOLLOW_DATA)
                            .map(data -> data.getBoolean(TAG_FOLLOWING))
                            .orElse(false);

    private CCFFollowHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // 仅在服务端处理，避免客户端预测导致的位置不同步。
        if (player.level().isClientSide()) {
            return;
        }

        List<ItemEntity> items = player.level().getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(CCFConfig.getMaxFollowDistance()),
                FOLLOWING_FILTER
        );
        if (items.isEmpty()) {
            return;
        }

        // 功能关闭时一次性释放所有跟随掉落物，不进入逐项更新逻辑。
        if (!CCFMod.followEnabled) {
            items.forEach(CCFFollowHandler::releaseFollowItem);
            return;
        }

        for (ItemEntity item : items) {
            updateFollowItem(player, item);
        }
    }

    private static void updateFollowItem(Player player, ItemEntity item) {
        CompoundTag data = item.getExistingData(CCFAttachments.FOLLOW_DATA).orElse(null);
        if (data == null) {
            releaseFollowItem(item);
            return;
        }

        // 保证物理始终处于关闭状态（防止被其他逻辑重新启用）。
        if (!item.isNoGravity()) {
            item.setNoGravity(true);
        }

        int ticks = data.getInt(TAG_FOLLOW_TICKS);
        if (ticks <= 0) {
            // 跟随时间耗尽：释放附件，恢复正常掉落物行为。
            releaseFollowItem(item);
            return;
        }
        data.putInt(TAG_FOLLOW_TICKS, ticks - 1);

        double targetX = player.getX();
        double targetY = player.getY() + TARGET_Y_OFFSET;
        double targetZ = player.getZ();
        double dx = targetX - item.getX();
        double dy = targetY - item.getY();
        double dz = targetZ - item.getZ();
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (distanceSq < SNAP_DISTANCE * SNAP_DISTANCE) {
            // 已到达：位置对齐，速度归零。
            item.setPos(targetX, targetY, targetZ);
            item.setDeltaMovement(Vec3.ZERO);
        } else {
            // 速度大小与距离线性相关，方向精确指向目标。
            double distance = Math.sqrt(distanceSq);
            double speed = Math.min(distance * SPEED_FACTOR, MAX_SPEED);
            double scale = speed / distance;
            item.setDeltaMovement(dx * scale, dy * scale, dz * scale);
        }

        // 强制服务端在下一 tick 同步位置到客户端，避免客户端自行物理预测。
        item.hurtMarked = true;
    }

    /**
     * 释放跟随状态：移除附件，恢复重力。
     * 调用后该掉落物回到普通掉落物的物理行为。
     */
    private static void releaseFollowItem(ItemEntity item) {
        item.removeData(CCFAttachments.FOLLOW_DATA);
        item.setNoGravity(false);
    }
}
