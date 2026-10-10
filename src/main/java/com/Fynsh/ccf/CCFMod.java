package com.Fynsh.ccf;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(CCFMod.MODID)
public class CCFMod {
    public static final String MODID = "ccf";

    /** 飞行功能是否启用（由 /ccf <bool> 控制） */
    public static boolean flightEnabled = true;

    /** 掉落物功能是否启用（由 /ccf if_follow <bool> 控制）
     *  包含两部分：方块挖掘直接入包、背包满时的跟随掉落物 */
    public static boolean followEnabled = true;

    public CCFMod(FMLJavaModLoadingContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
        MinecraftForge.EVENT_BUS.register(CCFConfig.class);
        MinecraftForge.EVENT_BUS.register(CCFBlockHandler.class);
        MinecraftForge.EVENT_BUS.register(CCFFollowHandler.class);

        CCFConfig.register(context);
    }
}