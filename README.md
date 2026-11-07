# Chinese Can Fly (CCF)

轻量、**仅客户端**的 Minecraft 模组：当游戏语言属于中文系时，双击空格即可在生存/冒险模式下飞行；挖掘产生的掉落物会自动进入背包，放不下的部分会跟随玩家。

本仓库按 Minecraft 版本与加载器分分支并行维护。

## 分支

| 分支 | 平台 | Minecraft | Java | 说明 |
|---|---|---|---|---|
| [`1.20.1-forge`](https://github.com/Fynsh/Chinese-Can-Fly/tree/1.20.1-forge) | Forge 47.4.10 | 1.20.1 | 17 | 当前主维护版本 |
| [`1.21.1-neoforge`](https://github.com/Fynsh/Chinese-Can-Fly/tree/1.21.1-neoforge) | NeoForge 21.1.251 | 1.21.1 | 21 | NeoForge 移植版本 |

两个分支独立构建、独立发布，彼此不共享 Gradle 配置。

## 快速开始

```bash
# Forge 1.20.1
git clone -b 1.20.1-forge https://github.com/Fynsh/Chinese-Can-Fly.git
cd Chinese-Can-Fly
./gradlew build

# NeoForge 1.21.1
git clone -b 1.21.1-neoforge https://github.com/Fynsh/Chinese-Can-Fly.git
cd Chinese-Can-Fly
./gradlew build
```

构建产物位于 `build/libs/ccf-1.4.3.jar`。

## 功能概览

- 中文语言环境下，生存/冒险模式双击空格切换飞行
- 飞行状态跨维度、重启存档与重启游戏保留
- 落地自动退出飞行
- 飞行状态下不再受到滞空挖掘惩罚
- 挖掘掉落物直接入包，背包满时转为跟随掉落物
- `/ccf flight`、`/ccf if_follow` 开关按存档保存
- 客户端指令 `/ccf` 管理飞行、掉落物、语言列表与跟随时间

> 本模组只注册客户端逻辑，飞行能力同步与掉落物捕获依赖单人/局域网的集成服务端；连接远程专用服务器时不会生效。

## 许可证

MIT，详见 [LICENSE](LICENSE)。
