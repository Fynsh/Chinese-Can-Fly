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

/**
 * 客户端飞行逻辑。
 *
 * <p>只有同时满足“功能开启、当前语言属于中文系、游戏模式为生存/冒险”时，
 * 双击空格才会切换飞行。飞行状态通过客户端与服务端的 {@link Abilities}
 * 双端同步；落地、切换模式、关闭功能或切换语言时自动撤销。</p>
 */
@Mod.EventBusSubscriber(modid = CCFMod.MODID, value = Dist.CLIENT)
public final class ClientEventHandler {

    private static final long DOUBLE_TAP_WINDOW_MS = 500L;
    private static final long GRANT_GRACE_MS = 300L;

    /** 每 20 tick 与服务端兜底同步一次能力，避免其他逻辑重置服务端状态。 */
    private static final int SERVER_SYNC_INTERVAL_TICKS = 20;

    private static long lastJumpPressMs = -10000L;
    private static long lastGrantMs = -10000L;

    private static boolean modFlight;
    private static boolean pendingRestore;
    private static GameType lastGameType;

    /** 上一 tick 是否在地面。 */
    private static boolean wasOnGroundLastTick = true;
    /** 上一 tick 是否处于飞行状态（用于区分“飞行落地”和“跳跃落地”）。 */
    private static boolean wasFlyingLastTick;
    /** 距离下一次兜底同步还剩多少 tick。 */
    private static int serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;

    private ClientEventHandler() {
    }

    // ==================== 条件 ====================

    private static boolean isChinese() {
        LanguageManager languageManager = Minecraft.getInstance().getLanguageManager();
        return languageManager != null && CCFConfig.isChinese(languageManager.getSelected());
    }

    private static boolean isSurvivalOrAdventure(GameType gameType) {
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private static boolean canActivate() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null) {
            return false;
        }
        return CCFMod.flightEnabled
                && isChinese()
                && isSurvivalOrAdventure(minecraft.gameMode.getPlayerMode());
    }

    // ==================== 持久化 ====================

    private static Path statePath() {
        return FMLPaths.CONFIGDIR.get().resolve("ccf_state.properties");
    }

    private static void saveState() {
        Properties properties = new Properties();
        properties.setProperty("modFlight", Boolean.toString(modFlight));
        try (OutputStream output = Files.newOutputStream(statePath())) {
            properties.store(output, "CCF mod flight state");
        } catch (IOException ignored) {
            // 状态文件写入失败不影响本次游戏内的飞行。
        }
    }

    private static void loadState() {
        Path path = statePath();
        if (!Files.exists(path)) {
            modFlight = false;
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
            modFlight = Boolean.parseBoolean(properties.getProperty("modFlight", "false"));
        } catch (IOException e) {
            modFlight = false;
        }
    }

    // ==================== 每 tick ====================

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            resetTransientState();
            return;
        }

        // 当前存档的开关状态可能尚未读取（登录事件早于集成服务端就绪）。
        CCFWorldState.ensureLoaded();

        // 登录后第一 tick 恢复飞行状态。
        if (pendingRestore) {
            pendingRestore = false;
            if (modFlight && canActivate()) {
                applyClient(player, true);
                applyServer(player, true);
                lastGrantMs = System.currentTimeMillis();
                serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;
            }
        }

        Abilities abilities = player.getAbilities();
        GameType currentGameType = minecraft.gameMode == null ? null : minecraft.gameMode.getPlayerMode();
        boolean onGround = player.onGround();
        boolean flying = abilities.flying;
        boolean inGracePeriod = System.currentTimeMillis() - lastGrantMs < GRANT_GRACE_MS;

        // 模式切换：非生存/冒险 → 生存/冒险，且此前处于模组飞行状态时恢复飞行。
        if (lastGameType != null && currentGameType != null && lastGameType != currentGameType) {
            boolean wasSurvivalOrAdventure = isSurvivalOrAdventure(lastGameType);
            boolean isSurvivalOrAdventure = isSurvivalOrAdventure(currentGameType);
            if (!wasSurvivalOrAdventure && isSurvivalOrAdventure && modFlight && canActivate()) {
                applyClient(player, true);
                applyServer(player, true);
                lastGrantMs = System.currentTimeMillis();
                serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;
            }
        }
        lastGameType = currentGameType;

        if (currentGameType == null) {
            wasOnGroundLastTick = onGround;
            wasFlyingLastTick = flying;
            return;
        }

        // 创造/旁观：只同步 modFlight = flying，不干涉原版飞行。
        if (!isSurvivalOrAdventure(currentGameType)) {
            if (modFlight != flying) {
                modFlight = flying;
                saveState();
            }
            wasOnGroundLastTick = onGround;
            wasFlyingLastTick = flying;
            return;
        }

        // 生存/冒险但当前条件不再满足：撤销飞行。
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

        // 落地检测：从空中到地面的边沿 + 上一 tick 在飞行。
        boolean landedFromFlight = !inGracePeriod
                && !wasOnGroundLastTick
                && onGround
                && wasFlyingLastTick;
        if (landedFromFlight) {
            wasOnGroundLastTick = true;
            wasFlyingLastTick = false;
            revoke(player);
            return;
        }

        if (flying) {
            maintain(player);
        }

        wasOnGroundLastTick = onGround;
        wasFlyingLastTick = abilities.flying;
    }

    private static void resetTransientState() {
        modFlight = false;
        pendingRestore = false;
        lastGameType = null;
        wasOnGroundLastTick = true;
        wasFlyingLastTick = false;
    }

    // ==================== 双击空格 ====================

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || event.getKey() != GLFW.GLFW_KEY_SPACE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null || !canActivate()) {
            return;
        }

        long now = System.currentTimeMillis();
        long delta = now - lastJumpPressMs;
        if (delta > 0 && delta <= DOUBLE_TAP_WINDOW_MS) {
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
        serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;

        // 地面起飞时给一个向上的初速度，模拟原版双击飞行的“起跳”行为。
        if (player.onGround()) {
            Vec3 movement = player.getDeltaMovement();
            player.setDeltaMovement(movement.x, 0.42D, movement.z);
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

    /**
     * 维持飞行：客户端能力被重置时立即补回；否则每
     * {@link #SERVER_SYNC_INTERVAL_TICKS} tick 与服务端兜底同步一次。
     */
    private static void maintain(LocalPlayer player) {
        Abilities abilities = player.getAbilities();
        boolean clientLostFlight = !abilities.mayfly || !abilities.flying;

        if (clientLostFlight) {
            applyClient(player, true);
            applyServer(player, true);
            serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;
            return;
        }

        if (--serverSyncCooldown <= 0) {
            applyServer(player, true);
            serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;
        }
    }

    private static void applyClient(LocalPlayer player, boolean active) {
        Abilities abilities = player.getAbilities();
        abilities.mayfly = active;
        abilities.flying = active;
    }

    /**
     * 把能力状态同步到集成服务端；远程服务器没有本地服务端，直接跳过。
     */
    private static void applyServer(LocalPlayer player, boolean active) {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return;
        }

        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(player.getUUID());
            if (serverPlayer == null) {
                return;
            }

            Abilities abilities = serverPlayer.getAbilities();
            boolean changed = false;
            if (abilities.mayfly != active) {
                abilities.mayfly = active;
                changed = true;
            }
            if (abilities.flying != active) {
                abilities.flying = active;
                changed = true;
            }
            if (!active) {
                serverPlayer.resetFallDistance();
            }
            if (changed) {
                serverPlayer.onUpdateAbilities();
            }
        });
    }

    public static void revokeFlight() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && modFlight) {
            revoke(player);
        }
    }

    // ==================== 登录 / 重生 ====================

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        CCFWorldState.reset();
        CCFWorldState.ensureLoaded();
        loadState();
        pendingRestore = modFlight;
        lastGameType = null;
        wasOnGroundLastTick = true;
        wasFlyingLastTick = false;
        lastJumpPressMs = -10000L;
        lastGrantMs = -10000L;
        serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CCFWorldState.reset();
        resetTransientState();
    }

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        lastJumpPressMs = -10000L;
        lastGrantMs = -10000L;
        lastGameType = null;
        serverSyncCooldown = SERVER_SYNC_INTERVAL_TICKS;
    }
}
