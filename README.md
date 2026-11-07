# Chinese Can Fly (CCF) — Forge 1.20.1

轻量、**仅客户端**的 Minecraft Forge 模组：当游戏语言属于中文系时，双击空格即可在生存/冒险模式下飞行。

- Minecraft：1.20.1
- Forge：47.4.10+
- Java：17
- 模组版本：1.4.3
- 许可证：MIT

## 分支说明

本仓库按版本并行维护：

| 分支 | 平台 | Minecraft | Java |
|---|---|---|---|
| `1.20.1-forge` | Forge 47.4.10 | 1.20.1 | 17 |
| `1.21.1-neoforge` | NeoForge 21.1.251 | 1.21.1 | 21 |

当前分支：`1.20.1-forge`。

## 功能

- **双击空格飞行**：在生存/冒险模式下切换飞行，操作方式与创造模式一致。
- **中文语言识别**：默认支持 `zh_cn`、`zh_tw`、`zh_hk`、`lzh`，可在配置中扩展。
- **仅生存/冒险生效**：创造与旁观模式由原版接管。
- **落地自动退出**：从飞行状态落地后自动取消飞行。
- **状态保留**：飞行状态跨维度、重启存档和重启游戏保留。
- **创造 → 生存继承**：在创造模式飞行中切换到生存模式后仍保持飞行。
- **飞行挖掘修正**：消除飞行状态下的滞空挖掘惩罚。
- **掉落物自动入包**：挖掘产生的掉落物直接进入背包，放不下的部分变成跟随掉落物。
- **按存档保存开关**：`/ccf flight` 与 `/ccf if_follow` 的状态保存在当前存档的 `ccf/state.properties`，不同存档互不影响。

> 本模组只注册客户端逻辑。飞行能力同步和掉落物捕获都依赖单人/局域网的集成服务端；连接远程专用服务器时不会生效。

## 指令

所有指令均为客户端指令，只在单人/局域网中有实际意义。

| 指令 | 说明 |
|---|---|
| `/ccf flight <true/false>` | 开关飞行功能（按存档保存） |
| `/ccf if_follow <true/false>` | 开关掉落物入包/跟随（按存档保存） |
| `/ccf following_time <ticks>` | 设置背包满时掉落物跟随时间 |
| `/ccf langs add <lang>` | 添加被视为中文的语言代码 |
| `/ccf langs addAll` | 写入 `All`，所有语言都视为中文 |
| `/ccf langs remove <lang>` | 移除语言 |
| `/ccf langs removeAll` | 清空语言列表 |
| `/ccf langs replace <old> <new>` | 替换语言 |
| `/ccf langs replaceAll <lang>` | 列表只保留指定语言 |

## 配置

客户端配置位于 `config/ccf-client.toml`：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `chineseLanguages` | `zh_cn, zh_tw, zh_hk, lzh` | 被视为中文的语言代码；包含 `All` 时匹配所有语言 |
| `followingTime` | `600` tick | 背包满时掉落物跟随持续时间 |
| `maxFollowDistance` | `16.0` 格 | 跟随掉落物搜索半径 |

飞行状态文件位于 `config/ccf_state.properties`；两个功能开关按存档保存在 `<存档>/ccf/state.properties`。

## 构建

需要 JDK 17：

```bash
./gradlew build
```

产物：

```text
build/libs/ccf-1.4.3.jar
```

开发运行：

```bash
./gradlew runClient
./gradlew runServer
```

## 许可证

MIT，详见 [LICENSE](LICENSE)。
