package com.Fynsh.ccf;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(CCFMod.MODID)
public class CCFMod {
    public static final String MODID = "ccf";

    /** 功能是否启用（由 /ccf 命令控制） */
    public static boolean enabled = true;

    /**
     * 模组主构造函数。
     * 通过构造函数参数注入 FMLJavaModLoadingContext。
     *
     * @param context Forge 自动注入的模组加载上下文
     */
    public CCFMod(FMLJavaModLoadingContext context) {
        // 仅客户端加载：如果是专用服务端，直接跳过
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        // 注册客户端事件处理类到 Forge 游戏事件总线
        // 其中包括：语言检测、双击飞行、客户端命令注册
        MinecraftForge.EVENT_BUS.register(ClientEventHandler.class);
    }
}