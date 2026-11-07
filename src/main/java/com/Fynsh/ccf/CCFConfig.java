package com.Fynsh.ccf;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端配置。
 *
 * <p>注意：{@link ModConfigEvent} 是模组总线事件，因此本类必须注册在
 * {@link Mod.EventBusSubscriber.Bus#MOD} 上；注册到 Forge 总线会导致
 * 配置加载/重载回调永远不触发。</p>
 */
@Mod.EventBusSubscriber(modid = CCFMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CCFConfig {

    /** 默认被视为“中文系”的语言代码。 */
    private static final List<String> DEFAULT_LANGUAGES = List.of("zh_cn", "zh_tw", "zh_hk", "lzh");

    /** 特殊值：写入列表后，{@link #isChinese(String)} 对任意非 null 语言代码恒为 true。 */
    public static final String ALL_LANGUAGES = "All";

    private static final int DEFAULT_FOLLOWING_TIME = 600;
    private static final double DEFAULT_MAX_FOLLOW_DISTANCE = 16.0D;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHINESE_LANGUAGES;
    public static final ForgeConfigSpec.IntValue FOLLOWING_TIME;
    public static final ForgeConfigSpec.DoubleValue MAX_FOLLOW_DISTANCE;
    public static final ForgeConfigSpec SPEC;

    private static ModConfig modConfig;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment(
                "Chinese Can Fly 配置",
                "",
                "chineseLanguages: 被视为\"中文系\"的语言代码列表。",
                "  - 可包含具体语言代码（如 zh_cn、zh_tw）",
                "  - 若包含 \"" + ALL_LANGUAGES + "\"，则视为所有语言均为中文",
                "followingTime: 背包满时挖掘掉落物跟随玩家的持续时间（tick）。",
                "maxFollowDistance: 跟随掉落物的最大搜索距离（方块）。"
        );

        CHINESE_LANGUAGES = builder
                .comment("被视为中文的语言代码列表")
                .defineListAllowEmpty(
                        List.of("chineseLanguages"),
                        new ArrayList<>(DEFAULT_LANGUAGES),
                        o -> o instanceof String
                );

        FOLLOWING_TIME = builder
                .comment("掉落物跟随玩家的持续时间（tick）")
                .defineInRange("followingTime", DEFAULT_FOLLOWING_TIME, 0, Integer.MAX_VALUE);

        MAX_FOLLOW_DISTANCE = builder
                .comment("跟随掉落物的最大搜索距离（方块）")
                .defineInRange("maxFollowDistance", DEFAULT_MAX_FOLLOW_DISTANCE, 1.0D, 128.0D);

        SPEC = builder.build();
    }

    private CCFConfig() {
    }

    public static void register(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    /**
     * 判断给定语言代码是否属于配置中定义的“中文系”。
     * 若列表包含 {@link #ALL_LANGUAGES}，则任何非 null 语言代码都视为中文。
     * 配置尚未加载时回退到默认列表。
     *
     * <p>该方法每客户端 tick 都会被调用，因此直接读取配置值，不做列表拷贝。</p>
     */
    public static boolean isChinese(String languageCode) {
        if (languageCode == null) {
            return false;
        }

        List<? extends String> languages;
        try {
            languages = CHINESE_LANGUAGES.get();
        } catch (IllegalStateException e) {
            languages = DEFAULT_LANGUAGES;
        }

        return languages != null
                && (languages.contains(ALL_LANGUAGES) || languages.contains(languageCode));
    }

    /**
     * 读取当前语言列表。
     * 返回可变副本，调用方可以自由修改而不影响 Forge 内部的缓存。
     * 配置尚未加载时返回默认列表的副本。
     */
    public static List<String> getLanguages() {
        try {
            List<? extends String> languages = CHINESE_LANGUAGES.get();
            return languages == null ? new ArrayList<>(DEFAULT_LANGUAGES) : new ArrayList<>(languages);
        } catch (IllegalStateException e) {
            return new ArrayList<>(DEFAULT_LANGUAGES);
        }
    }

    /**
     * 覆盖写入语言列表并保存到 {@code ccf-client.toml}。
     *
     * @return 配置尚未加载时返回 {@code false} 且不做任何修改
     */
    public static boolean setLanguages(List<String> languages) {
        try {
            CHINESE_LANGUAGES.set(List.copyOf(languages));
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
            return DEFAULT_FOLLOWING_TIME;
        }
    }

    /**
     * 设置跟随持续时间并保存配置。
     *
     * @return 配置尚未加载时返回 {@code false}
     */
    public static boolean setFollowingTime(int ticks) {
        try {
            FOLLOWING_TIME.set(ticks);
        } catch (IllegalStateException e) {
            return false;
        }
        saveConfig();
        return true;
    }

    public static double getMaxFollowDistance() {
        try {
            return MAX_FOLLOW_DISTANCE.get();
        } catch (IllegalStateException e) {
            return DEFAULT_MAX_FOLLOW_DISTANCE;
        }
    }

    private static void saveConfig() {
        if (modConfig != null) {
            modConfig.save();
        }
    }

    /**
     * 配置加载与重载共用同一个回调：两者都只是更新持有的 {@link ModConfig} 句柄。
     */
    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (CCFMod.MODID.equals(event.getConfig().getModId())) {
            modConfig = event.getConfig();
        }
    }
}
