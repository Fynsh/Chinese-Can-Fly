package com.Fynsh.ccf;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

/**
 * 掉落物跟随行为处理器。
 *
 * 对带有 {@code CCF_Following} 标签的 {@link ItemEntity} 施加“位置伺服”式跟随：
 * 每 tick 计算物品到玩家脚边的向量，用与距离成正比的速度朝目标推进。
 * 距离越近速度越小，接触玩家时速度归零，物品最终停在玩家脚边。
 *
 * 关闭重力与碰撞（noGravity / noPhysics），使物品能够穿过方块紧贴玩家，
 * 不受地形与抛物线影响。每 tick 标记 hurtMarked 以强制服务端同步位置到客户端，
 * 避免客户端自身的物理预测覆盖服务端轨迹。
 *
 * 当掉落物功能被关闭、或跟随时间耗尽时，通过 {@link #releaseFollowItem} 释放标签，
 * 恢复物品的正常掉落物物理。
 */
public class CCFFollowHandler {

    /** 搜索范围：足以覆盖玩家走出若干步后仍处于范围内的跟随物品 */
    private static final double FOLLOW_RANGE = 16.0;

    /** 速度 = 距离 × 此系数。距离越近速度越小，接触时为 0 */
    private static final double SPEED_FACTOR = 0.4;

    /** 速度上限，避免距离过远时单 tick 位移过大造成视觉上的瞬移 */
    private static final double MAX_SPEED = 1.2;

    /** 到达该距离以内直接吸附到目标点，消除线性衰减带来的渐近残留 */
    private static final double SNAP_DISTANCE = 0.05;

    /** 目标点相对玩家脚部的竖直偏移，略高以避免与玩家碰撞箱重叠 */
    private static final double TARGET_Y_OFFSET = 0.1;

    /** 持久化数据键：标记该掉落物处于跟随状态 */
    private static final String TAG_FOLLOWING = "CCF_Following";

    /** 持久化数据键：剩余跟随时间（tick） */
    private static final String TAG_FOLLOW_TICKS = "CCF_FollowTicks";

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        // 仅在服务端处理，避免客户端预测导致的位置不同步
        if (player.level().isClientSide()) return;

        List<ItemEntity> items = player.level().getEntitiesOfClass(
                ItemEntity.class,
                player.getBoundingBox().inflate(FOLLOW_RANGE)
        );

        for (ItemEntity item : items) {
            if (!item.getPersistentData().getBoolean(TAG_FOLLOWING)) continue;

            // 掉落物功能被关闭：立即释放所有跟随掉落物
            if (!CCFMod.followEnabled) {
                releaseFollowItem(item);
                continue;
            }

            // 保证物理始终处于关闭状态（防止被其他逻辑重新启用）
            if (!item.isNoGravity()) item.setNoGravity(true);

            int ticks = item.getPersistentData().getInt(TAG_FOLLOW_TICKS);
            if (ticks <= 0) {
                // 跟随时间耗尽：释放标签，恢复正常掉落物行为
                releaseFollowItem(item);
                continue;
            }
            item.getPersistentData().putInt(TAG_FOLLOW_TICKS, ticks - 1);

            // 目标：玩家脚边，略高 0.1 避免与玩家碰撞箱重叠
            Vec3 target = new Vec3(
                    player.getX(),
                    player.getY() + TARGET_Y_OFFSET,
                    player.getZ()
            );
            Vec3 toTarget = target.subtract(item.position());
            double distance = toTarget.length();

            if (distance < SNAP_DISTANCE) {
                // 已到达：位置对齐，速度归零
                item.setPos(target.x, target.y, target.z);
                item.setDeltaMovement(Vec3.ZERO);
            } else {
                // 速度大小与距离线性相关，方向精确指向目标
                double speed = Math.min(distance * SPEED_FACTOR, MAX_SPEED);
                Vec3 direction = toTarget.scale(1.0 / distance); // 单位向量
                item.setDeltaMovement(direction.scale(speed));
            }

            // 强制服务端在下一 tick 同步位置到客户端，避免客户端自行物理预测
            item.hurtMarked = true;
        }
    }

    /**
     * 释放跟随状态：移除标签，恢复重力与碰撞。
     * 调用后该掉落物回到普通掉落物的物理行为。
     */
    private static void releaseFollowItem(ItemEntity item) {
        item.getPersistentData().remove(TAG_FOLLOWING);
        item.getPersistentData().remove(TAG_FOLLOW_TICKS);
        item.setNoGravity(false);
    }
}