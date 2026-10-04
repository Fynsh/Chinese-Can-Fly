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
public class CommonEventHandler {

    /**
     * 消除模组飞行状态下的浮空挖掘惩罚。
     *
     * 原版 Player.getDigSpeed() 逻辑：
     *   if (!onGround()) f /= 5.0F;
     *   f = ForgeEventFactory.getBreakSpeed(this, state, pos, f);  // 事件发出时 f 已被惩罚
     *
     * 因此 BreakSpeed 事件里 originalSpeed 已经是惩罚后的值，
     * 我们要做的是把它乘回 5。
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (player == null) return;

        boolean flying = player.getAbilities().flying;
        boolean onGround = player.onGround();

        if (flying && !onGround) {
            event.setNewSpeed(event.getNewSpeed() * 5.0F);
        }
    }
}