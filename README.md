# BingoGame

`dev.Jonsen233.BingoGame` is a Paper 1.21.8 Minecraft Bingo plugin. It uses the standard `src/main` Maven layout and Java 21. The card, temporary vanilla dimensions, GUI, map, chat, tab list, sidebar, spectators, reconnect snapshots, and MongoDB statistics are server-side features.

## Build

```powershell
mvn clean package
```

The build requires JDK 21 and Maven 3.9 or newer. On Windows, use `mvnw.cmd` if
you add the Maven Wrapper to your checkout.

Install `target/BingoGame-1.0.0.jar` into the Paper server `plugins` directory. MongoDB is enabled by default for player statistics and round history. The default URI is `mongodb://localhost:27017`; configure `plugins/BingoGame/config.yml` for a hosted instance. If MongoDB is temporarily unavailable, the plugin keeps the round running and uses an in-memory statistics fallback until the next restart.

## Commands

- `/bingo join` and `/bingo leave` manage the queue.
- `/bingo board` opens the five by five card.
- `/bingo map` gives the server-rendered vanilla map card.
- `/bingo spectate` or `/bingo watch` joins an active round as a protected spectator.
- `/bingo stats` shows persistent player statistics.
- `/bingo top` shows the wins leaderboard.
- `/bingo start` and `/bingo stop` require `bingogame.admin`.
- `/bingo reload` reloads the configuration and requires `bingogame.admin`. It also reloads `tab.yml`, `scoreboard.yml`, and `saying.yml` without restarting the server.

Each round creates fresh vanilla Overworld, Nether, and End worlds with the same round seed and structures enabled (the latter two can be disabled with `game.enable-nether` and `game.enable-end`). Players can use normal portals inside the round dimensions, are isolated from the main world, and have their inventory and player state restored after the round. All temporary worlds are unloaded and deleted. Progress is detected by a periodic inventory scan plus acquisition events, so crafting, smelting, fishing, containers and item pickup are covered. A row, column, or diagonal wins by default; set `game.win-condition: FULL_CARD` for blackout mode.

The built-in card pool contains more than 25 vanilla-obtainable goals across the enabled dimensions. When Nether or End is disabled, dimension-specific goals are removed automatically. To use a curated pool, set `game.goal-pool` to at least 25 unique Bukkit material names (the optional `minecraft:` prefix is accepted). Invalid, duplicate, non-item, or disabled-dimension goals are rejected and the round falls back to the built-in pool when `game.validate-goal-pool` is false. The board GUI marks collected goals in green and supports configurable title, item names, slot labels, and lore in the `display` section.

`/bingo map` gives a normal vanilla filled map rendered entirely by the plugin. It shows the five by five card, short item codes, completed cells, and remaining time; no client mod, resource pack, or custom asset is required. `/bingo spectate` lets non-participants observe an active round and view its board/map while remaining in spectator mode.

Player snapshots are written to `plugins/BingoGame/snapshots.yml` before a player enters a round. This protects inventories and player state across reconnects and plugin/server restarts; snapshots are removed only after the original state has been restored.

MongoDB collections are created automatically and indexed by UUID, wins, games, items, and streak. Database errors switch the plugin to memory-only statistics instead of disabling the game.

## UI configuration

The files `plugins/BingoGame/tab.yml`, `plugins/BingoGame/scoreboard.yml`, and `plugins/BingoGame/saying.yml` are independent and are copied from the jar on first start. They replace the old hard-coded display code. Changes take effect with `/bingo reload`.

All three files accept `&` color codes, PlaceholderAPI `%...%` placeholders when PlaceholderAPI is installed, and the direct LuckPerms variables `{luckperms_prefix}`, `%luckperms_prefix%`, `{luckperms_suffix}`, `%luckperms_suffix%`, `{luckperms_group}`, `%luckperms_group%`, `{luckperms_primary_group_name}`, and `%luckperms_primary_group_name%`. LuckPerms is optional; missing data resolves to an empty value. Common variables include `{player}`, `{player_name}`, `{online_players}`, `{max_players}`, and `{server}`.

`scoreboard.yml` additionally provides Bingo state variables such as `{status}`, `{progress}`, `{remaining_seconds}`, `{time}`, `{active_players}`, `{wins}`, `{player_rank}`, and `{leader_1_player}` through `{leader_3_score}`. The `{leaderboard}` marker expands using the configured rank and `self-line` entries. `saying.yml` uses `{message}` for the original chat component and keeps LuckPerms/PlaceholderAPI resolution on the main thread before Paper's asynchronous renderer runs.

The tab list, sidebar, chat format, five-by-five GUI, and vanilla filled-map card use Paper/Bukkit server APIs. Players do not need a client mod, resource pack, custom font, or other client-side files.
