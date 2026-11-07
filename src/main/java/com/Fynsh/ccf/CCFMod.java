package com.Fynsh.ccf;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 模组入口。
 *
 * <p>本模组是纯客户端模组：所有事件处理器都通过 {@link Mod.EventBusSubscriber}
 * 按物理端自动注册，专用服务器上不会注册任何玩法逻辑。</p>
 */
@Mod(CCFMod.MODID)
public final class CCFMod {

    public static final String MODID = "ccf";

    /** 飞行功能是否启用（由 {@code /ccf flight} 控制，按存档保存）。 */
    public static volatile boolean flightEnabled = true;

    /**
     * 掉落物功能是否启用（由 {@code /ccf if_follow} 控制，按存档保存）。
     * 包含两部分：方块挖掘直接入包、背包满时的跟随掉落物。
     */
    public static volatile boolean followEnabled = true;

    public CCFMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        CCFConfig.register(context);
    }
}
