package com.Fynsh.ccf;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 按存档保存的客户端开关状态。
 *
 * <p>状态文件位于当前存档根目录下的 {@code ccf/state.properties}，
 * 因此不同存档之间互不影响。远程服务器没有本地存档目录，
 * 此时既不读取也不写入，开关只在本会话内生效。</p>
 *
 * <p>只有在集成服务端（单人/局域网）中 {@link Minecraft#getSingleplayerServer()}
 * 才非空；这也是本模组飞行与掉落物功能的实际生效场景。</p>
 */
public final class CCFWorldState {

    /** 存档内的状态目录名。 */
    private static final String STATE_DIR = "ccf";

    /** 状态文件名。 */
    private static final String STATE_FILE = "state.properties";

    private static final String FLIGHT_KEY = "flightEnabled";
    private static final String FOLLOW_KEY = "followEnabled";

    /** 当前是否已经读取过某个存档的状态。 */
    private static boolean loaded;

    /** 已读取状态对应的文件路径，用于识别切换存档。 */
    private static Path loadedPath;

    private CCFWorldState() {
    }

    /**
     * 离开世界或切换到另一个存档时调用，先把开关恢复为默认值。
     * 随后第一次 {@link #ensureLoaded()} 会从新存档读取真实值。
     */
    public static void reset() {
        loaded = false;
        loadedPath = null;
        CCFMod.flightEnabled = true;
        CCFMod.followEnabled = true;
    }

    /**
     * 确保当前存档的状态已经读取。
     *
     * <p>登录事件触发时集成服务端可能尚未就绪，因此调用方可以在客户端 tick 中
     * 重复调用；路径未变化时不会重复读盘。</p>
     */
    public static void ensureLoaded() {
        Path path = currentPath();
        if (path == null || (loaded && path.equals(loadedPath))) {
            return;
        }

        Properties properties = new Properties();
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ignored) {
                // 读取失败时使用默认值，不影响本次游戏。
            }
        }

        CCFMod.flightEnabled = Boolean.parseBoolean(properties.getProperty(FLIGHT_KEY, "true"));
        CCFMod.followEnabled = Boolean.parseBoolean(properties.getProperty(FOLLOW_KEY, "true"));
        loaded = true;
        loadedPath = path;
    }

    /**
     * 把当前开关写入当前存档。
     * 没有本地存档目录（远程服务器/主菜单）时不做任何事。
     */
    public static void save() {
        Path path = currentPath();
        if (path == null) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty(FLIGHT_KEY, Boolean.toString(CCFMod.flightEnabled));
        properties.setProperty(FOLLOW_KEY, Boolean.toString(CCFMod.followEnabled));

        try {
            Files.createDirectories(path.getParent());
            try (OutputStream output = Files.newOutputStream(path)) {
                properties.store(output, "Chinese Can Fly per-save switches");
            }
            loaded = true;
            loadedPath = path;
        } catch (IOException ignored) {
            // 写入失败不影响本次游戏内的开关。
        }
    }

    /**
     * 当前存档根目录下的状态文件；只有集成服务端才存在本地存档路径。
     */
    private static Path currentPath() {
        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            return null;
        }
        return server.getWorldPath(LevelResource.ROOT).resolve(STATE_DIR).resolve(STATE_FILE);
    }
}
