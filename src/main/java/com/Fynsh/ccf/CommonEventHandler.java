package com.Fynsh.ccf;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 通用事件处理器（客户端 + 服务端都会注册）。
 * 用于处理需要服务端逻辑的事件（如 BreakSpeed）。
 */
@Mod.EventBusSubscriber(modid = CCFMod.MODID)
public final class CommonEventHandler {

    private CommonEventHandler() {
    }

    /**
     * 消除模组飞行状态下的浮空挖掘惩罚。
     *
     * <p>原版 {@code Player#getDigSpeed()} 的逻辑是：</p>
     * <pre>
     *   if (!onGround()) f /= 5.0F;
     *   f = ForgeEventFactory.getBreakSpeed(this, state, pos, f);
     * </pre>
     * <p>因此 {@code BreakSpeed} 事件里的速度已经是惩罚后的值，
     * 这里把它乘回 5 即可恢复地面挖掘速度。</p>
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null) {
            return;
        }

        if (player.getAbilities().flying && !player.onGround()) {
            event.setNewSpeed(event.getNewSpeed() * 5.0F);
        }
    }
}
