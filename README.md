# BingoGame

`dev.Jonsen233.BingoGame` 是一个 Paper 1.21.8 的 Minecraft Bingo 插件。它使用标准的 `src/main` Maven 布局和 Java 21。卡片、临时原版维度、GUI、地图、聊天、Tab 列表、侧边栏、旁观者、重连快照和 MongoDB 统计都是服务器端功能。

## 构建

```powershell
mvn clean package
```

构建需要 JDK 21 和 Maven 3.9 或更高版本。在 Windows 上，如果将 Maven Wrapper 添加到你的检出目录，请使用 `mvnw.cmd`。

将 `target/BingoGame-1.0.0.jar` 安装到 Paper 服务器的 `plugins` 目录中。MongoDB 默认启用，用于玩家统计和回合历史。默认 URI 是 `mongodb://localhost:27017`；为托管实例配置 `plugins/BingoGame/config.yml`。如果 MongoDB 暂时不可用，插件会保持回合运行，并使用内存统计回退，直到下次重启。

## 命令

- `/bingo join` 和 `/bingo leave` 管理队列。
- `/bingo board` 打开 5x5 卡片。
- `/bingo map` 给予服务器渲染的原版地图卡片。
- `/bingo spectate` 或 `/bingo watch` 以受保护的旁观者身份加入活跃回合。
- `/bingo stats` 显示持久化玩家统计。
- `/bingo top` 显示胜场排行榜。
- `/bingo start` 和 `/bingo stop` 需要 `bingogame.admin`。
- `/bingo reload` 重新加载配置，并且需要 `bingogame.admin`。它还会重新加载 `tab.yml`、`scoreboard.yml` 和 `saying.yml`，无需重启服务器。

每个回合都会创建新的原版主世界、下界和末地世界，使用相同的回合种子并启用结构（后两者可通过 `game.enable-nether` 和 `game.enable-end` 禁用）。玩家可以在回合维度内使用普通传送门，与主世界隔离，并在回合结束后恢复物品栏和玩家状态。所有临时世界都会被卸载并删除。进度通过定期物品栏扫描和获取事件检测，因此涵盖合成、熔炼、钓鱼、容器和物品拾取。默认情况下，一行、一列或一条对角线即可获胜；设置 `game.win-condition: FULL_CARD` 以启用全卡模式。

内置卡片池包含超过 25 个可在已启用维度中通过原版方式获取的目标。当 Nether 或 End 被禁用时，维度特定目标会自动移除。要使用精选池，请将 `game.goal-pool` 设置为至少 25 个唯一的 Bukkit 材料名称（可接受可选的 `minecraft:` 前缀）。无效、重复、非物品或已禁用维度的目标会被拒绝；当 `game.validate-goal-pool` 为 false 时，回合会回退到内置池。棋盘 GUI 用绿色标记已收集目标，并支持在 `display` 部分配置标题、物品名称、槽位标签和描述文本。

`/bingo map` 提供一张完全由插件渲染的普通原版已填充地图。它显示 5x5 卡片、简短物品代码、已完成格子和剩余时间；无需客户端模组、资源包或自定义资源。`/bingo spectate` 让非参与者观察活跃回合，并在保持旁观模式的同时查看其棋盘/地图。

玩家快照会在玩家进入回合前写入 `plugins/BingoGame/snapshots.yml`。这会在重连以及插件/服务器重启期间保护物品栏和玩家状态；只有在原始状态恢复后才会移除快照。

MongoDB 集合会自动创建，并按 UUID、胜场、对局数、物品和连胜建立索引。数据库错误会将插件切换为仅内存统计，而不是禁用游戏。

## UI 配置

文件 `plugins/BingoGame/tab.yml`、`plugins/BingoGame/scoreboard.yml` 和 `plugins/BingoGame/saying.yml` 相互独立，并在首次启动时从 jar 复制。它们取代旧的硬编码显示代码。更改通过 `/bingo reload` 生效。

所有三个文件都接受 `&` 颜色代码、安装 PlaceholderAPI 时的 PlaceholderAPI `%...%` 占位符，以及直接的 LuckPerms 变量 `{luckperms_prefix}`、`%luckperms_prefix%`、`{luckperms_suffix}`、`%luckperms_suffix%`、`{luckperms_group}`、`%luckperms_group%`、`{luckperms_primary_group_name}` 和 `%luckperms_primary_group_name%`。LuckPerms 是可选的；缺失数据会解析为空值。常用变量包括 `{player}`、`{player_name}`、`{online_players}`、`{max_players}` 和 `{server}`。

`scoreboard.yml` 还提供 Bingo 状态变量，例如 `{status}`、`{progress}`、`{remaining_seconds}`、`{time}`、`{active_players}`、`{wins}`、`{player_rank}`，以及从 `{leader_1_player}` 到 `{leader_3_score}`。`{leaderboard}` 标记会使用配置的排名和 `self-line` 条目展开。`saying.yml` 使用 `{message}` 表示原始聊天组件，并在 Paper 的异步渲染器运行之前，将 LuckPerms/PlaceholderAPI 解析保留在主线程上。

Tab 列表、侧边栏、聊天格式、5x5 GUI 和原版已填充地图卡片使用 Paper/Bukkit 服务器 API。玩家不需要客户端模组、资源包、自定义字体或其他客户端文件。
