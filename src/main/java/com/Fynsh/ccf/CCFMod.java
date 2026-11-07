package com.Fynsh.ccf;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * 模组入口。
 *
 * <p>本模组是纯客户端模组：{@link Mod#dist()} 限定只在物理客户端加载，
 * 专用服务器上不会创建入口实例。所有玩法事件都通过
 * {@link net.neoforged.fml.common.EventBusSubscriber} 按物理端自动注册。</p>
 */
@Mod(value = CCFMod.MODID, dist = Dist.CLIENT)
public final class CCFMod {

    public static final String MODID = "ccf";

    /** 飞行功能是否启用（由 {@code /ccf flight} 控制，按存档保存）。 */
    public static volatile boolean flightEnabled = true;

    /**
     * 掉落物功能是否启用（由 {@code /ccf if_follow} 控制，按存档保存）。
     * 包含两部分：方块挖掘直接入包、背包满时的跟随掉落物。
     */
    public static volatile boolean followEnabled = true;

    public CCFMod(IEventBus modEventBus, ModContainer modContainer) {
        CCFAttachments.ATTACHMENT_TYPES.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.CLIENT, CCFConfig.SPEC);
    }
}
