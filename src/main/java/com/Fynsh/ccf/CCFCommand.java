package com.Fynsh.ccf;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = CCFMod.MODID, value = Dist.CLIENT)
public class CCFCommand {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("ccf")
                        // ccf flight <bool>
                        .then(Commands.literal("flight")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            CCFMod.flightEnabled = v;
                                            if (!v) {
                                                ClientEventHandler.revokeFlight();
                                            }
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.translatable(
                                                            v ? "ccf.command.flight.enabled"
                                                                    : "ccf.command.flight.disabled"),
                                                    false);
                                            return 1;
                                        })))

                        // ccf if_follow <bool>
                        .then(Commands.literal("if_follow")
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> {
                                            boolean v = BoolArgumentType.getBool(ctx, "value");
                                            CCFMod.followEnabled = v;
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.translatable(
                                                            v ? "ccf.command.follow.enabled"
                                                                    : "ccf.command.follow.disabled"),
                                                    false);
                                            return 1;
                                        })))

                        // ccf following_time <ticks>
                        .then(Commands.literal("following_time")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            int ticks = IntegerArgumentType.getInteger(ctx, "ticks");
                                            CCFConfig.setFollowingTime(ticks);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.translatable(
                                                            "ccf.command.following_time.set", ticks),
                                                    false);
                                            return 1;
                                        })))

                        // ccf langs ...
                        .then(Commands.literal("langs")
                                .then(buildAdd())
                                .then(buildAddAll())
                                .then(buildRemove())
                                .then(buildRemoveAll())
                                .then(buildReplace())
                                .then(buildReplaceAll()))
        );
    }

    // ============================================================
    //  /ccf langs add <lang>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> buildAdd() {
        return Commands.literal("add")
                .then(Commands.argument("lang", StringArgumentType.word())
                        .executes(ctx -> {
                            String lang = StringArgumentType.getString(ctx, "lang");
                            List<String> langs = CCFConfig.getLanguages();
                            if (langs.contains(lang)) {
                                ctx.getSource().sendSuccess(
                                        () -> Component.translatable(
                                                "ccf.command.langs.add.exists", lang),
                                        false);
                                return 0;
                            }
                            langs.add(lang);
                            if (!CCFConfig.setLanguages(langs)) {
                                ctx.getSource().sendFailure(
                                        Component.translatable("ccf.command.config.not_loaded"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable(
                                            "ccf.command.langs.add.success", lang),
                                    false);
                            return 1;
                        }));
    }

    // ============================================================
    //  /ccf langs addAll
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> buildAddAll() {
        return Commands.literal("addAll")
                .executes(ctx -> {
                    List<String> langs = new ArrayList<>();
                    langs.add(CCFConfig.ALL_LANGS);
                    if (!CCFConfig.setLanguages(langs)) {
                        ctx.getSource().sendFailure(
                                Component.translatable("ccf.command.config.not_loaded"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(
                            () -> Component.translatable(
                                    "ccf.command.langs.addall.success", CCFConfig.ALL_LANGS),
                            false);
                    return 1;
                });
    }

    // ============================================================
    //  /ccf langs remove <lang>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> buildRemove() {
        return Commands.literal("remove")
                .then(Commands.argument("lang", StringArgumentType.word())
                        .executes(ctx -> {
                            String lang = StringArgumentType.getString(ctx, "lang");
                            List<String> langs = CCFConfig.getLanguages();
                            if (!langs.contains(lang)) {
                                ctx.getSource().sendSuccess(
                                        () -> Component.translatable(
                                                "ccf.command.langs.remove.missing", lang),
                                        false);
                                return 0;
                            }
                            langs.remove(lang);
                            if (!CCFConfig.setLanguages(langs)) {
                                ctx.getSource().sendFailure(
                                        Component.translatable("ccf.command.config.not_loaded"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable(
                                            "ccf.command.langs.remove.success", lang),
                                    false);
                            return 1;
                        }));
    }

    // ============================================================
    //  /ccf langs removeAll
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> buildRemoveAll() {
        return Commands.literal("removeAll")
                .executes(ctx -> {
                    if (!CCFConfig.setLanguages(new ArrayList<>())) {
                        ctx.getSource().sendFailure(
                                Component.translatable("ccf.command.config.not_loaded"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(
                            () -> Component.translatable(
                                    "ccf.command.langs.removeall.success"),
                            false);
                    return 1;
                });
    }

    // ============================================================
    //  /ccf langs replace <lang1> <lang2>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> buildReplace() {
        return Commands.literal("replace")
                .then(Commands.argument("lang1", StringArgumentType.word())
                        .then(Commands.argument("lang2", StringArgumentType.word())
                                .executes(ctx -> {
                                    String lang1 = StringArgumentType.getString(ctx, "lang1");
                                    String lang2 = StringArgumentType.getString(ctx, "lang2");
                                    List<String> langs = CCFConfig.getLanguages();
                                    if (!langs.contains(lang1)) {
                                        ctx.getSource().sendFailure(
                                                Component.translatable(
                                                        "ccf.command.langs.replace.missing", lang1));
                                        return 0;
                                    }
                                    int idx = langs.indexOf(lang1);
                                    langs.set(idx, lang2);
                                    if (!CCFConfig.setLanguages(langs)) {
                                        ctx.getSource().sendFailure(
                                                Component.translatable("ccf.command.config.not_loaded"));
                                        return 0;
                                    }
                                    ctx.getSource().sendSuccess(
                                            () -> Component.translatable(
                                                    "ccf.command.langs.replace.success", lang1, lang2),
                                            false);
                                    return 1;
                                })));
    }

    // ============================================================
    //  /ccf langs replaceAll <lang>
    // ============================================================
    private static LiteralArgumentBuilder<CommandSourceStack> buildReplaceAll() {
        return Commands.literal("replaceAll")
                .then(Commands.argument("lang", StringArgumentType.word())
                        .executes(ctx -> {
                            String lang = StringArgumentType.getString(ctx, "lang");
                            List<String> langs = new ArrayList<>();
                            langs.add(lang);
                            if (!CCFConfig.setLanguages(langs)) {
                                ctx.getSource().sendFailure(
                                        Component.translatable("ccf.command.config.not_loaded"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(
                                    () -> Component.translatable(
                                            "ccf.command.langs.replaceall.success", lang),
                                    false);
                            return 1;
                        }));
    }
}