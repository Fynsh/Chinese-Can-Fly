package com.Fynsh.ccf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@Mod.EventBusSubscriber(modid = CCFMod.MODID, value = Dist.CLIENT)
public class ClientEventHandler {

    private static final long DOUBLE_TAP_WINDOW_MS = 500L;
    private static final long GRANT_GRACE_MS = 300L;
    private static long lastJumpPressMs = -10000L;
    private static long lastGrantMs = -10000L;

    private static boolean modFlight = false;
    private static boolean pendingRestore = false;
    private static GameType lastGameType = null;

    /** 上一 tick 是否在地面 */
    private static boolean wasOnGroundLastTick = true;
    /** 上一 tick 是否处于飞行状态（用于区分"飞行落地"和"跳跃落地"） */
    private static boolean wasFlyingLastTick = false;

    // ==================== 条件 ====================

    private static boolean isChinese() {
        LanguageManager lm = Minecraft.getInstance().getLanguageManager();
        if (lm == null) return false;
        return CCFConfig.isChinese(lm.getSelected());
    }

    private static boolean isSurvivalOrAdventure(GameType t) {
        return t == GameType.SURVIVAL || t == GameType.ADVENTURE;
    }

    private static boolean canActivate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        return CCFMod.flightEnabled && isChinese()
                && isSurvivalOrAdventure(mc.gameMode.getPlayerMode());
    }

    // ==================== 持久化 ====================

    private static Path statePath() {
        return FMLPaths.CONFIGDIR.get().resolve("ccf_state.properties");
    }

    private static void saveState() {
        Properties p = new Properties();
        p.setProperty("modFlight", Boolean.toString(modFlight));
        try (OutputStream os = Files.newOutputStream(statePath())) {
            p.store(os, "CCF mod flight state");
        } catch (IOException ignored) {
        }
    }

    private static void loadState() {
        Path path = statePath();
        if (!Files.exists(path)) {
            modFlight = false;
            return;
        }
        Properties p = new Properties();
        try (InputStream is = Files.newInputStream(path)) {
            p.load(is);
            modFlight = Boolean.parseBoolean(p.getProperty("modFlight", "false"));
        } catch (IOException e) {
            modFlight = false;
        }
    }

    // ==================== 每 tick ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            modFlight = false;
            pendingRestore = false;
            lastGameType = null;
            wasOnGroundLastTick = true;
            wasFlyingLastTick = false;
            return;
        }

        // 登录后第一 tick 恢复飞行状态
        if (pendingRestore) {
            pendingRestore = false;
            if (modFlight && canActivate()) {
                applyClient(player, true);
                applyServer(player, true);
                lastGrantMs = System.currentTimeMillis();
            }
        }

        Abilities ab = player.getAbilities();
        GameType currentType = mc.gameMode == null ? null : mc.gameMode.getPlayerMode();
        boolean onGround = player.onGround();
        boolean flying = ab.flying;
        boolean inGrace = System.currentTimeMillis() - lastGrantMs < GRANT_GRACE_MS;

        // 模式切换检测：非生存/冒险 → 生存/冒险，且 modFlight 为 true 时恢复飞行
        if (lastGameType != null && currentType != null && lastGameType != currentType) {
            boolean wasSA = isSurvivalOrAdventure(lastGameType);
            boolean isSA = isSurvivalOrAdventure(currentType);
            if (!wasSA && isSA && modFlight && canActivate()) {
                applyClient(player, true);
                applyServer(player, true);
                lastGrantMs = System.currentTimeMillis();
            }
        }
        lastGameType = currentType;

        if (currentType == null) {
            wasOnGroundLastTick = onGround;
            wasFlyingLastTick = flying;
            return;
        }

        // 创造/旁观：只同步 modFlight = flying，不干涉原版飞行
        if (!isSurvivalOrAdventure(currentType)) {
            if (modFlight != flying) {
                modFlight = flying;
                saveState();
            }
            wasOnGroundLastTick = onGround;
            wasFlyingLastTick = flying;
            return;
        }

        // 生存/冒险
        if (!canActivate()) {
            if (modFlight) {
                revoke(player);
            }
            wasOnGroundLastTick = onGround;
            wasFlyingLastTick = false;
            return;
        }

        if (!modFlight) {
            wasOnGroundLastTick = onGround;
            wasFlyingLastTick = flying;
            return;
        }

        // 落地检测：从空中到地面的边沿 + 上一 tick 在飞行
        boolean landedFromFlight = !inGrace
                && !wasOnGroundLastTick && onGround
                && wasFlyingLastTick;
        if (landedFromFlight) {
            wasOnGroundLastTick = onGround;
            wasFlyingLastTick = false;
            revoke(player);
            return;
        }

        // 维持飞行
        if (flying) {
            maintain(player);
        }

        wasOnGroundLastTick = onGround;
        wasFlyingLastTick = ab.flying;
    }

    // ==================== 双击空格 ====================

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() != GLFW.GLFW_KEY_SPACE) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        LocalPlayer player = mc.player;
        if (player == null || !canActivate()) return;

        long now = System.currentTimeMillis();
        long dt = now - lastJumpPressMs;
        if (dt > 0 && dt <= DOUBLE_TAP_WINDOW_MS) {
            lastJumpPressMs = -10000L;
            if (player.getAbilities().flying) {
                revoke(player);
            } else {
                grant(player);
            }
        } else {
            lastJumpPressMs = now;
        }
    }

    // ==================== 状态操作 ====================

    private static void grant(LocalPlayer player) {
        modFlight = true;
        lastGrantMs = System.currentTimeMillis();
        // 地面起飞时给一个向上的初速度，模拟原版双击飞行的"起跳"行为
        if (player.onGround()) {
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, 0.42D, v.z);
            player.hasImpulse = true;
        }
        applyClient(player, true);
        applyServer(player, true);
        saveState();
    }

    private static void revoke(LocalPlayer player) {
        modFlight = false;
        applyClient(player, false);
        applyServer(player, false);
        saveState();
    }

    private static void maintain(LocalPlayer player) {
        Abilities ab = player.getAbilities();
        if (!ab.mayfly || !ab.flying) {
            applyClient(player, true);
        }
        applyServer(player, true);
    }

    private static void applyClient(LocalPlayer player, boolean active) {
        Abilities ab = player.getAbilities();
        ab.mayfly = active;
        ab.flying = active;
    }

    private static void applyServer(LocalPlayer player, boolean active) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
            if (sp == null) return;
            Abilities sb = sp.getAbilities();
            boolean changed = false;
            if (sb.mayfly != active) { sb.mayfly = active; changed = true; }
            if (sb.flying != active) { sb.flying = active; changed = true; }
            if (!active) sp.resetFallDistance();
            if (changed) {
                sp.onUpdateAbilities();
            }
        });
    }

    public static void revokeFlight() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && modFlight) revoke(p);
    }

    // ==================== 登录 / 重生 ====================

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        loadState();
        pendingRestore = modFlight;
        lastGameType = null;
        wasOnGroundLastTick = true;
        wasFlyingLastTick = false;
        lastJumpPressMs = -10000L;
        lastGrantMs = -10000L;
    }

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        lastJumpPressMs = -10000L;
        lastGrantMs = -10000L;
        lastGameType = null;
    }
}