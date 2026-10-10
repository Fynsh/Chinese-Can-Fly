package com.Fynsh.ccf;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.List;

public class CCFConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final List<String> DEFAULT_LANGS =
            List.of("zh_cn", "zh_tw", "zh_hk", "lzh");

    /** 特殊值：写入列表后，isChinese 对任意非 null 语言代码恒为 true */
    public static final String ALL_LANGS = "All";

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHINESE_LANGUAGES;

    /** 跟随时间（单位：tick），默认 600 tick = 30 秒 */
    public static final ForgeConfigSpec.IntValue FOLLOWING_TIME;

    public static final ForgeConfigSpec SPEC;

    private static ModConfig modConfig;

    static {
        BUILDER.comment(
                "Chinese Can Fly 配置",
                "",
                "chineseLanguages: 被视为\"中文系\"的语言代码列表。",
                "  - 可包含具体语言代码（如 zh_cn、zh_tw）",
                "  - 若包含 \"" + ALL_LANGS + "\"，则视为所有语言均为中文",
                "followingTime: 背包满时挖掘掉落物跟随玩家的持续时间（tick）。"
        );

        CHINESE_LANGUAGES = BUILDER
                .comment("被视为中文的语言代码列表")
                .defineListAllowEmpty(
                        List.of("chineseLanguages"),
                        DEFAULT_LANGS,
                        o -> o instanceof String
                );

        FOLLOWING_TIME = BUILDER
                .comment("掉落物跟随玩家的持续时间（tick）")
                .defineInRange("followingTime", 600, 0, Integer.MAX_VALUE);

        SPEC = BUILDER.build();
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    /**
     * 判断给定语言代码是否属于配置中定义的"中文系"。
     * 若列表包含 {@link #ALL_LANGS}，则任何非 null 语言代码都视为中文。
     * 配置尚未加载时回退到默认列表。
     */
    public static boolean isChinese(String langCode) {
        if (langCode == null) return false;
        List<String> langs = getLanguages();
        if (langs.contains(ALL_LANGS)) return true;
        return langs.contains(langCode);
    }

    /**
     * 读取当前语言列表。
     * 返回的是可变副本，调用方可以自由修改而不影响 Forge 内部的缓存。
     * 配置尚未加载时返回默认列表的副本。
     */
    public static List<String> getLanguages() {
        try {
            List<? extends String> raw = CHINESE_LANGUAGES.get();
            return new ArrayList<>(raw);
        } catch (IllegalStateException e) {
            return new ArrayList<>(DEFAULT_LANGS);
        }
    }

    /**
     * 覆盖写入语言列表并保存到 ccf-client.toml。
     *
     * @return 配置尚未加载时返回 false 且不做任何修改
     */
    public static boolean setLanguages(List<String> langs) {
        try {
            CHINESE_LANGUAGES.set(langs);
        } catch (IllegalStateException e) {
            return false;
        }
        saveConfig();
        return true;
    }

    public static int getFollowingTime() {
        try {
            return FOLLOWING_TIME.get();
        } catch (IllegalStateException e) {
            return 600;
        }
    }

    public static void setFollowingTime(int ticks) {
        FOLLOWING_TIME.set(ticks);
        saveConfig();
    }

    private static void saveConfig() {
        if (modConfig != null) {
            modConfig.save();
        }
    }

    @SubscribeEvent
    public static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals(CCFMod.MODID)) {
            modConfig = event.getConfig();
        }
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals(CCFMod.MODID)) {
            modConfig = event.getConfig();
        }
    }
}