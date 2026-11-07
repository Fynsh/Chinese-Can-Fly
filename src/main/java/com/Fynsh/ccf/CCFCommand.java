package com.Fynsh.ccf;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端指令 {@code /ccf}。
 *
 * <p>通过 {@link RegisterClientCommandsEvent} 注册，只存在于客户端，
 * 因此这些指令只对单人/本地集成服务器有意义。</p>
 *
 * <p>{@code /ccf flight} 与 {@code /ccf if_follow} 的开关状态会写入当前存档的
 * {@code ccf/state.properties}，不同存档之间互不影响。</p>
 */
@EventBusSubscriber(modid = CCFMod.MODID, value = Dist.CLIENT)
public final class CCFCommand {

    private CCFCommand() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ccf")
                        .then(flight())
                        .then(ifFollow())
                        .then(followingTime())
                        .then(Commands.literal("langs")
                                .then(addLanguage())
                                .then(addAllLanguages())
                                .then(removeLanguage())
                                .then(removeAllLanguages())
                                .then(replaceLanguage())
                                .then(replaceAllLanguages()))
        );
    }

    // ============================================================
    //  /ccf flight <bool>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> flight() {
        return Commands.literal("flight")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean enabled = BoolArgumentType.getBool(context, "value");
                            CCFWorldState.ensureLoaded();
                            CCFMod.flightEnabled = enabled;
                            CCFWorldState.save();
                            if (!enabled) {
                                ClientEventHandler.revokeFlight();
                            }
                            success(context.getSource(),
                                    enabled ? "ccf.command.flight.enabled" : "ccf.command.flight.disabled");
                            return 1;
                        }));
    }

    // ============================================================
    //  /ccf if_follow <bool>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> ifFollow() {
        return Commands.literal("if_follow")
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(context -> {
                            boolean enabled = BoolArgumentType.getBool(context, "value");
                            CCFWorldState.ensureLoaded();
                            CCFMod.followEnabled = enabled;
                            CCFWorldState.save();
                            success(context.getSource(),
                                    enabled ? "ccf.command.follow.enabled" : "ccf.command.follow.disabled");
                            return 1;
                        }));
    }

    // ============================================================
    //  /ccf following_time <ticks>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> followingTime() {
        return Commands.literal("following_time")
                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                        .executes(context -> {
                            int ticks = IntegerArgumentType.getInteger(context, "ticks");
                            if (!CCFConfig.setFollowingTime(ticks)) {
                                configNotLoaded(context.getSource());
                                return 0;
                            }
                            success(context.getSource(), "ccf.command.following_time.set", ticks);
                            return 1;
                        }));
    }

    // ============================================================
    //  /ccf langs add <lang>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> addLanguage() {
        return Commands.literal("add")
                .then(Commands.argument("lang", StringArgumentType.word())
                        .executes(context -> {
                            String language = StringArgumentType.getString(context, "lang");
                            List<String> languages = CCFConfig.getLanguages();
                            if (languages.contains(language)) {
                                success(context.getSource(), "ccf.command.langs.add.exists", language);
                                return 0;
                            }
                            languages.add(language);
                            if (!applyLanguages(context.getSource(), languages)) {
                                return 0;
                            }
                            success(context.getSource(), "ccf.command.langs.add.success", language);
                            return 1;
                        }));
    }

    // ============================================================
    //  /ccf langs addAll
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> addAllLanguages() {
        return Commands.literal("addAll")
                .executes(context -> {
                    List<String> languages = new ArrayList<>();
                    languages.add(CCFConfig.ALL_LANGUAGES);
                    if (!applyLanguages(context.getSource(), languages)) {
                        return 0;
                    }
                    success(context.getSource(), "ccf.command.langs.addall.success", CCFConfig.ALL_LANGUAGES);
                    return 1;
                });
    }

    // ============================================================
    //  /ccf langs remove <lang>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> removeLanguage() {
        return Commands.literal("remove")
                .then(Commands.argument("lang", StringArgumentType.word())
                        .executes(context -> {
                            String language = StringArgumentType.getString(context, "lang");
                            List<String> languages = CCFConfig.getLanguages();
                            if (!languages.remove(language)) {
                                success(context.getSource(), "ccf.command.langs.remove.missing", language);
                                return 0;
                            }
                            if (!applyLanguages(context.getSource(), languages)) {
                                return 0;
                            }
                            success(context.getSource(), "ccf.command.langs.remove.success", language);
                            return 1;
                        }));
    }

    // ============================================================
    //  /ccf langs removeAll
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> removeAllLanguages() {
        return Commands.literal("removeAll")
                .executes(context -> {
                    if (!applyLanguages(context.getSource(), new ArrayList<>())) {
                        return 0;
                    }
                    success(context.getSource(), "ccf.command.langs.removeall.success");
                    return 1;
                });
    }

    // ============================================================
    //  /ccf langs replace <lang1> <lang2>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> replaceLanguage() {
        return Commands.literal("replace")
                .then(Commands.argument("lang1", StringArgumentType.word())
                        .then(Commands.argument("lang2", StringArgumentType.word())
                                .executes(context -> {
                                    String source = StringArgumentType.getString(context, "lang1");
                                    String target = StringArgumentType.getString(context, "lang2");
                                    List<String> languages = CCFConfig.getLanguages();
                                    int index = languages.indexOf(source);
                                    if (index < 0) {
                                        failure(context.getSource(), "ccf.command.langs.replace.missing", source);
                                        return 0;
                                    }
                                    languages.set(index, target);
                                    if (!applyLanguages(context.getSource(), languages)) {
                                        return 0;
                                    }
                                    success(context.getSource(), "ccf.command.langs.replace.success", source, target);
                                    return 1;
                                })));
    }

    // ============================================================
    //  /ccf langs replaceAll <lang>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> replaceAllLanguages() {
        return Commands.literal("replaceAll")
                .then(Commands.argument("lang", StringArgumentType.word())
                        .executes(context -> {
                            String language = StringArgumentType.getString(context, "lang");
                            List<String> languages = new ArrayList<>();
                            languages.add(language);
                            if (!applyLanguages(context.getSource(), languages)) {
                                return 0;
                            }
                            success(context.getSource(), "ccf.command.langs.replaceall.success", language);
                            return 1;
                        }));
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    /**
     * 写回语言列表；配置尚未加载时发送统一的失败提示。
     */
    private static boolean applyLanguages(CommandSourceStack source, List<String> languages) {
        if (CCFConfig.setLanguages(languages)) {
            return true;
        }
        configNotLoaded(source);
        return false;
    }

    private static void configNotLoaded(CommandSourceStack source) {
        source.sendFailure(Component.translatable("ccf.command.config.not_loaded"));
    }

    private static void success(CommandSourceStack source, String translationKey, Object... arguments) {
        source.sendSuccess(() -> Component.translatable(translationKey, arguments), false);
    }

    private static void failure(CommandSourceStack source, String translationKey, Object... arguments) {
        source.sendFailure(Component.translatable(translationKey, arguments));
    }
}
