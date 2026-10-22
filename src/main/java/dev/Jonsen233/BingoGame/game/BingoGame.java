package dev.Jonsen233.BingoGame.game;

import dev.Jonsen233.BingoGame.BingoGamePlugin;
import dev.Jonsen233.BingoGame.data.StatsService;
import dev.Jonsen233.BingoGame.data.SnapshotStore;
import dev.Jonsen233.BingoGame.ui.BingoMapRenderer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class BingoGame {
    private final BingoGamePlugin plugin;
    private final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
    private final SnapshotStore snapshotStore;
    private final Map<UUID, Set<Integer>> collected = new HashMap<>();
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final Map<UUID, Integer> lastProgress = new HashMap<>();
    private GameState state = GameState.WAITING;
    private BingoBoard board;
    private World world;
    private World netherWorld;
    private World endWorld;
    private long seed;
    private long endAt;
    private int countdown;
    private BukkitTask ticker;
    private UUID winner;
    private BossBar bossBar;

    public BingoGame(BingoGamePlugin plugin) {
        this.plugin = plugin;
        this.snapshotStore = new SnapshotStore(plugin);
        this.snapshots.putAll(snapshotStore.load());
        if (!snapshots.isEmpty()) {
            plugin.getLogger().info("Loaded " + snapshots.size() + " persisted player snapshot(s).");
        }
    }

    public GameState state() { return state; }
    public World world() { return world; }
    /** Returns the round's Nether world, when Nether access is enabled. */
    public World netherWorld() { return netherWorld; }
    /** Returns the round's End world, when End access is enabled. */
    public World endWorld() { return endWorld; }
    /** True when the world belongs to this Bingo round. */
    public boolean isRoundWorld(World candidate) {
        return candidate != null && (candidate.equals(world) || candidate.equals(netherWorld) || candidate.equals(endWorld));
    }
    /** The worlds currently owned by the round, in stable dimension order. */
    public Set<World> roundWorlds() {
        Set<World> worlds = new HashSet<>();
        if (world != null) worlds.add(world);
        if (netherWorld != null) worlds.add(netherWorld);
        if (endWorld != null) worlds.add(endWorld);
        return Set.copyOf(worlds);
    }
    public BingoBoard board() { return board; }
    public boolean contains(Player player) { return participants.contains(player.getUniqueId()); }
    public boolean isSpectator(Player player) { return spectators.contains(player.getUniqueId()); }
    public int playerCount() { return participants.size(); }
    public Set<UUID> participants() { return Set.copyOf(participants); }
    public int progress(Player player) { return player == null ? 0 : progress(player.getUniqueId()); }
    /** Returns progress for an online or disconnected participant. */
    public int progress(UUID playerId) {
        return board == null || playerId == null ? 0 : board.progress(collected.getOrDefault(playerId, Set.of()));
    }
    public boolean hasCollected(Player player, int index) { return collected.getOrDefault(player.getUniqueId(), Set.of()).contains(index); }
    public long remainingSeconds() {
        return state == GameState.RUNNING ? Math.max(0L, (endAt - System.currentTimeMillis()) / 1000L) : 0L;
    }

    /** Remaining seconds in the pre-round countdown, for UI placeholders. */
    public int countdownSeconds() {
        return state == GameState.COUNTDOWN ? Math.max(0, countdown) : 0;
    }

    public boolean isBoardInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof BingoBoardHolder;
    }

    /** Removes round worlds left behind by a hard server stop. */
    public void cleanupOrphanWorlds() {
        String prefix = plugin.getConfig().getString("game.world-name-prefix", "bingo_");
        java.util.regex.Pattern roundName = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(prefix) + "[0-9a-fA-F]{8}(_nether|_end)?");
        Path container = Bukkit.getWorldContainer().toPath();
        try (var paths = Files.list(container)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> roundName.matcher(path.getFileName().toString()).matches())
                    .forEach(path -> {
                        World loaded = Bukkit.getWorld(path.getFileName().toString());
                        if (loaded != null && !Bukkit.unloadWorld(loaded, false)) {
                            plugin.getLogger().warning("Unable to unload orphan Bingo world " + loaded.getName());
                            return;
                        }
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteWorld(path));
                    });
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to scan for orphan Bingo worlds", exception);
        }
    }

    public void reconnect(Player player) {
        PlayerSnapshot saved = snapshots.get(player.getUniqueId());
        if (saved != null && !participants.contains(player.getUniqueId()) && state == GameState.WAITING) {
            restorePlayer(player);
            return;
        }
        if (state == GameState.RUNNING && participants.contains(player.getUniqueId()) && world != null) {
            // Re-apply round isolation because a disconnected player's game
            // inventory may have been saved by the server.
            collected.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>());
            lastProgress.putIfAbsent(player.getUniqueId(), 0);
            restoreRoundPresence(player, spawnFor(player, world.getSpawnLocation()));
            if (bossBar != null) bossBar.addPlayer(player);
            player.sendMessage(plugin.msg("game-start", Map.of()));
        } else if (state == GameState.RUNNING && spectators.contains(player.getUniqueId()) && world != null) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(world.getSpawnLocation());
            if (bossBar != null) bossBar.addPlayer(player);
        }
    }

    public boolean spectate(Player player) {
        if (state != GameState.RUNNING || world == null || participants.contains(player.getUniqueId())) return false;
        int maxSpectators = Math.max(0, plugin.getConfig().getInt("game.max-spectators", 32));
        if (!spectators.contains(player.getUniqueId()) && spectators.size() >= maxSpectators) return false;
        spectators.add(player.getUniqueId());
        captureSnapshot(player);
        player.closeInventory();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(world.getSpawnLocation());
        if (bossBar != null) bossBar.addPlayer(player);
        return true;
    }

    public boolean join(Player player) {
        if (state == GameState.RUNNING || state == GameState.ENDING) return false;
        int max = plugin.getConfig().getInt("game.max-players", 16);
        if (participants.contains(player.getUniqueId())) return true;
        if (participants.size() >= max) return false;
        participants.add(player.getUniqueId());
        captureSnapshot(player);
        player.sendMessage(plugin.msg("joined", Map.of("players", participants.size(), "max", max)));
        if (state == GameState.WAITING && participants.size() >= plugin.getConfig().getInt("game.min-players", 2)) {
            startCountdown();
        }
        return true;
    }

    public boolean leave(Player player) {
        if (spectators.remove(player.getUniqueId())) {
            if (bossBar != null) bossBar.removePlayer(player);
            restorePlayer(player);
            return true;
        }
        if (!participants.remove(player.getUniqueId())) return false;
        if (bossBar != null) bossBar.removePlayer(player);
        collected.remove(player.getUniqueId());
        lastProgress.remove(player.getUniqueId());
        restorePlayer(player);
        if (state == GameState.COUNTDOWN && participants.size() < plugin.getConfig().getInt("game.min-players", 2)) {
            state = GameState.WAITING;
            countdown = 0;
            broadcast(plugin.msg("need-players", Map.of("min", plugin.getConfig().getInt("game.min-players", 2))));
        }
        if (state == GameState.RUNNING && participants.isEmpty()) stop(false);
        return true;
    }

    public void startCountdown() {
        if (state != GameState.WAITING || participants.size() < plugin.getConfig().getInt("game.min-players", 2)) return;
        state = GameState.COUNTDOWN;
        countdown = Math.max(1, plugin.getConfig().getInt("game.countdown-seconds", 30));
        broadcast(plugin.msg("countdown", Map.of("seconds", countdown)));
    }

    public void forceStart() {
        if (state == GameState.WAITING || state == GameState.COUNTDOWN) {
            if (participants.size() >= plugin.getConfig().getInt("game.min-players", 2)) beginRound();
        }
    }

    public void tick() {
        if (state == GameState.COUNTDOWN) {
            if (participants.size() < plugin.getConfig().getInt("game.min-players", 2)) {
                state = GameState.WAITING;
                return;
            }
            if (--countdown <= 0) beginRound();
            else if (countdown <= 5 || countdown % 10 == 0) broadcast(plugin.msg("countdown", Map.of("seconds", countdown)));
        } else if (state == GameState.RUNNING) {
            scanPlayers();
            if (state == GameState.RUNNING && System.currentTimeMillis() >= endAt) finishByProgress();
            updateBossBar();
        }
    }

    private void beginRound() {
        seed = ThreadLocalRandom.current().nextLong();
        boolean netherEnabled = plugin.getConfig().getBoolean("game.enable-nether", true);
        boolean endEnabled = plugin.getConfig().getBoolean("game.enable-end", true);
        try {
            List<String> configuredGoals = plugin.getConfig().getStringList("game.goal-pool");
            board = configuredGoals.isEmpty()
                    ? BingoBoard.randomFromMaterials(new Random(seed), BingoBoard.defaultPool(netherEnabled, endEnabled))
                    : BingoBoard.random(new Random(seed), configuredGoals, netherEnabled, endEnabled);
        } catch (IllegalArgumentException exception) {
            if (!plugin.getConfig().getBoolean("game.validate-goal-pool", true)) {
                plugin.getLogger().log(Level.WARNING, "Invalid game.goal-pool; using built-in goals", exception);
                board = BingoBoard.randomFromMaterials(new Random(seed), BingoBoard.defaultPool(netherEnabled, endEnabled));
            } else {
                plugin.getLogger().log(Level.SEVERE, "Invalid game.goal-pool; round was not started", exception);
                state = GameState.WAITING;
                board = null;
                for (UUID uuid : new HashSet<>(participants)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null) restorePlayer(player);
                }
                participants.clear();
                collected.clear();
                lastProgress.clear();
                return;
            }
        }
        String name = plugin.getConfig().getString("game.world-name-prefix", "bingo_") + UUID.randomUUID().toString().substring(0, 8);
        world = createRoundWorld(name, World.Environment.NORMAL);
        if (plugin.getConfig().getBoolean("game.enable-nether", true)) {
            netherWorld = createRoundWorld(name + "_nether", World.Environment.NETHER);
        }
        if (plugin.getConfig().getBoolean("game.enable-end", true)) {
            endWorld = createRoundWorld(name + "_end", World.Environment.THE_END);
        }
        if (world == null || (plugin.getConfig().getBoolean("game.enable-nether", true) && netherWorld == null)
                || (plugin.getConfig().getBoolean("game.enable-end", true) && endWorld == null)) {
            plugin.getLogger().severe("Unable to create all Bingo dimension worlds");
            unloadAndDeleteWorlds();
            state = GameState.WAITING;
            for (UUID uuid : new HashSet<>(participants)) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null) restorePlayer(player);
            }
            participants.clear();
            spectators.clear();
            collected.clear();
            lastProgress.clear();
            board = null;
            winner = null;
            return;
        }
        state = GameState.RUNNING;
        configureRoundWorld(world);
        configureRoundWorld(netherWorld);
        configureRoundWorld(endWorld);
        Location spawn = safeLocation(world, 0, 0);
        world.setSpawnLocation(spawn);
        if (netherWorld != null) netherWorld.setSpawnLocation(safeLocation(netherWorld, 0, 0));
        if (endWorld != null) endWorld.setSpawnLocation(safeLocation(endWorld, 0, 0));
        endAt = System.currentTimeMillis() + plugin.getConfig().getLong("game.time-limit-seconds", 1800) * 1000L;
        if (plugin.getConfig().getBoolean("display.bossbar", true)) {
            bossBar = Bukkit.createBossBar(ChatColor.GOLD + "BINGO", BarColor.YELLOW, BarStyle.SEGMENTED_20);
        }
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            collected.put(uuid, new HashSet<>());
            lastProgress.put(uuid, 0);
            preparePlayerForRound(player, spawnFor(player, spawn));
            if (bossBar != null) bossBar.addPlayer(player);
            player.sendTitle(ChatColor.GOLD + "BINGO", ChatColor.YELLOW + "Collect items and complete a line!", 10, 60, 10);
        }
        broadcast(plugin.msg("game-start", Map.of()));
        plugin.getLogger().info("Bingo round started with seed " + seed + " in " + name);
    }

    private World createRoundWorld(String name, World.Environment environment) {
        WorldCreator creator = new WorldCreator(name)
                .environment(environment)
                .type(WorldType.NORMAL)
                .generateStructures(true)
                .seed(seed);
        return Bukkit.createWorld(creator);
    }

    private void configureRoundWorld(World roundWorld) {
        if (roundWorld == null) return;
        roundWorld.setDifficulty(org.bukkit.Difficulty.NORMAL);
        roundWorld.setPVP(plugin.getConfig().getBoolean("game.allow-pvp", false));
        roundWorld.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        roundWorld.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, true);
        roundWorld.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false);
        WorldBorder border = roundWorld.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(Math.max(128, plugin.getConfig().getDouble("game.world-border-size", 2048)));
    }

    /** Returns a safe spawn point for a particular round dimension. */
    public Location spawnForWorld(World roundWorld) {
        if (!isRoundWorld(roundWorld)) return null;
        Location spawn = roundWorld.getSpawnLocation();
        return safeLocation(roundWorld, spawn.getBlockX(), spawn.getBlockZ());
    }

    private void preparePlayerForRound(Player player, Location spawn) {
        player.closeInventory();
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0f);
        player.setLevel(0);
        player.setExp(0f);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        player.setAllowFlight(false);
        player.setFlying(false);
        player.teleport(spawn);
    }

    /** Reconnect isolation that preserves the player's current round inventory. */
    private void restoreRoundPresence(Player player, Location spawn) {
        player.closeInventory();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.teleport(spawn);
    }

    private Location spawnFor(Player player, Location center) {
        int slot = Math.max(0, new ArrayList<>(participants).indexOf(player.getUniqueId()));
        double angle = slot * Math.PI * 2 / Math.max(1, participants.size());
        Location point = center.clone().add(Math.cos(angle) * 4, 0, Math.sin(angle) * 4);
        return safeLocation(world, point.getBlockX(), point.getBlockZ());
    }

    private Location safeLocation(World world, int x, int z) {
        for (int attempt = 0; attempt < 64; attempt++) {
            int groundY = world.getHighestBlockYAt(x, z);
            while (groundY > world.getMinHeight() && (world.getBlockAt(x, groundY, z).isLiquid() || !world.getBlockAt(x, groundY, z).getType().isSolid())) groundY--;
            int feetY = groundY + 1;
            if (groundY > world.getMinHeight() && feetY + 1 < world.getMaxHeight()
                    && world.getBlockAt(x, feetY, z).isPassable()
                    && world.getBlockAt(x, feetY + 1, z).isPassable()) {
                return new Location(world, x + .5, feetY, z + .5);
            }
            x += ThreadLocalRandom.current().nextInt(-8, 9);
            z += ThreadLocalRandom.current().nextInt(-8, 9);
        }
        return world.getSpawnLocation();
    }

    private void scanPlayers() {
        for (UUID uuid : participants) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || world == null || !isRoundWorld(player.getWorld())) continue;
            Set<Integer> done = collected.computeIfAbsent(uuid, ignored -> new HashSet<>());
            for (int index = 0; index < BingoBoard.CELLS; index++) {
                if (!done.contains(index) && countMaterial(player, board.material(index)) > 0) {
                    done.add(index);
                    player.sendMessage(plugin.msg("item-found", Map.of("item", BingoBoard.displayName(board.material(index)), "progress", done.size())));
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, .6f, 1.5f);
                }
            }
            boolean fullCard = "FULL_CARD".equalsIgnoreCase(plugin.getConfig().getString("game.win-condition", "LINE"));
            if (fullCard ? board.isComplete(done) : board.hasLine(done)) {
                finish(player);
                return;
            }
        }
    }

    private int countMaterial(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) if (item != null && item.getType() == material) total += item.getAmount();
        return total;
    }

    public void onItemAcquired(Player player) {
        if (state == GameState.RUNNING && participants.contains(player.getUniqueId())) scanPlayers();
    }

    private void finish(Player player) {
        if (state != GameState.RUNNING) return;
        winner = player.getUniqueId();
        state = GameState.ENDING;
        broadcast(plugin.msg("finish", Map.of("winner", player.getName())));
        finishRound(false);
    }

    private void finishByProgress() {
        if (state != GameState.RUNNING) return;
        winner = participants.stream().max(Comparator.comparingInt(uuid -> collected.getOrDefault(uuid, Set.of()).size())).orElse(null);
        state = GameState.ENDING;
        int progress = winner == null ? 0 : collected.getOrDefault(winner, Set.of()).size();
        String name = winner == null || Bukkit.getPlayer(winner) == null ? "无人" : Bukkit.getPlayer(winner).getName();
        broadcast(plugin.msg("timeout", Map.of("winner", name, "progress", progress * 4)));
        finishRound(true);
    }

    public void stop(boolean announce) {
        if (state == GameState.WAITING && participants.isEmpty()) return;
        if (announce) broadcast(plugin.msg("game-stop", Map.of()));
        state = GameState.ENDING;
        // An administrator stop or server shutdown cancels the round; it is
        // not a completed game and must not inflate player statistics.
        finishRound(false, false);
    }

    private void finishRound(boolean timedOut) {
        finishRound(timedOut, true);
    }

    private void finishRound(boolean timedOut, boolean recordStats) {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        StatsService stats = plugin.stats();
        for (UUID uuid : new HashSet<>(participants)) {
            Player player = Bukkit.getPlayer(uuid);
            int items = collected.getOrDefault(uuid, Set.of()).size();
            if (recordStats && stats != null) {
                stats.recordGame(uuid, player == null ? uuid.toString() : player.getName(), uuid.equals(winner), items, timedOut);
            }
            if (player != null) restorePlayer(player);
        }
        for (UUID uuid : new HashSet<>(spectators)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) restorePlayer(player);
        }
        participants.clear();
        spectators.clear();
        collected.clear();
        lastProgress.clear();
        board = null;
        winner = null;
        state = GameState.WAITING;
        unloadAndDeleteWorlds();
    }

    private void restorePlayer(Player player) {
        PlayerSnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot != null) {
            snapshot.restore(player);
            snapshotStore.remove(player.getUniqueId());
        }
        if (Bukkit.getScoreboardManager() != null) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private void captureSnapshot(Player player) {
        UUID uuid = player.getUniqueId();
        if (snapshots.containsKey(uuid)) return;
        PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
        snapshots.put(uuid, snapshot);
        snapshotStore.put(uuid, snapshot);
    }

    private void deleteWorld(Path path) {
        String name = path.getFileName().toString();
        for (int attempt = 0; attempt < 5 && Files.exists(path); attempt++) {
            try {
                Files.walk(path).sorted(Comparator.reverseOrder()).forEach(file -> {
                    try { Files.deleteIfExists(file); } catch (IOException ignored) { }
                });
                if (Files.exists(path)) Thread.sleep(100L);
            } catch (Exception exception) {
                plugin.getLogger().log(Level.FINE, "Unable to delete Bingo world " + name, exception);
            }
        }
    }

    /** Unloads every dimension created for the current round and removes its files. */
    private void unloadAndDeleteWorlds() {
        List<World> worlds = new ArrayList<>();
        if (world != null) worlds.add(world);
        if (netherWorld != null) worlds.add(netherWorld);
        if (endWorld != null) worlds.add(endWorld);
        world = null;
        netherWorld = null;
        endWorld = null;
        for (World roundWorld : worlds) {
            String worldName = roundWorld.getName();
            if (!Bukkit.unloadWorld(roundWorld, false)) {
                plugin.getLogger().warning("Unable to unload Bingo world " + worldName);
                continue;
            }
            Path worldPath = Bukkit.getWorldContainer().toPath().resolve(worldName);
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteWorld(worldPath));
            } else {
                // During plugin shutdown the scheduler is about to stop, so a
                // direct best-effort cleanup prevents abandoned round worlds.
                deleteWorld(worldPath);
            }
        }
    }

    /** Updates only the round boss bar. The sidebar is owned by ScoreboardManager. */
    private void updateBossBar() {
        long seconds = Math.max(0, (endAt - System.currentTimeMillis()) / 1000);
        long limit = Math.max(1L, plugin.getConfig().getLong("game.time-limit-seconds", 1800));
        if (bossBar != null) {
            int best = participants.stream().mapToInt(uuid -> collected.getOrDefault(uuid, Set.of()).size()).max().orElse(0);
            bossBar.setProgress(Math.max(0D, Math.min(1D, seconds / (double) limit)));
            bossBar.setTitle(ChatColor.GOLD + "BINGO  " + ChatColor.YELLOW + best + "/25  " + ChatColor.WHITE + formatTime(seconds));
        }
    }

    private String formatTime(long seconds) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    public void openBoard(Player player) {
        if (board == null || (!participants.contains(player.getUniqueId()) && !spectators.contains(player.getUniqueId()))) {
            player.sendMessage(plugin.msg("not-joined", Map.of()));
            return;
        }
        Set<Integer> done = collected.getOrDefault(player.getUniqueId(), Set.of());
        BingoBoardHolder holder = new BingoBoardHolder();
        String title = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("display.board-title", "&6&lBINGO"));
        Inventory inventory = Bukkit.createInventory(holder, 45, title);
        holder.inventory(inventory);
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < 45; slot++) inventory.setItem(slot, filler);
        for (int index = 0; index < BingoBoard.CELLS; index++) {
            int row = index / 5;
            int column = index % 5;
            Material material = board.material(index);
            ItemStack item = new ItemStack(done.contains(index) ? Material.LIME_STAINED_GLASS_PANE : material);
            ItemMeta meta = item.getItemMeta();
            String format = plugin.getConfig().getString(done.contains(index) ? "display.board-complete-name" : "display.board-item-name", done.contains(index) ? "&a&l%item%" : "&f%item%");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', format.replace("%item%", BingoBoard.displayName(material))));
            if (plugin.getConfig().getBoolean("display.board-item-lore", true)) {
                String slot = plugin.getConfig().getString("display.board-slot-label", "&7Slot %slot%").replace("%slot%", String.valueOf(index + 1));
                String status = plugin.getConfig().getString(done.contains(index) ? "display.board-complete-label" : "display.board-incomplete-label", done.contains(index) ? "&aCollected" : "&eNot collected");
                meta.setLore(List.of(ChatColor.translateAlternateColorCodes('&', slot), ChatColor.translateAlternateColorCodes('&', status)));
            }
            item.setItemMeta(meta);
            inventory.setItem(row * 9 + column + 2, item);
        }
        player.openInventory(inventory);
    }

    /** Gives a vanilla filled map rendered by the plugin, with no client add-on. */
    public void giveMap(Player player) {
        if (board == null || (!participants.contains(player.getUniqueId()) && !spectators.contains(player.getUniqueId()))) {
            player.sendMessage(plugin.msg("not-joined", Map.of()));
            return;
        }
        for (ItemStack existing : player.getInventory().getContents()) {
            if (existing != null && existing.getType() == Material.FILLED_MAP && existing.hasItemMeta()
                    && existing.getItemMeta().getDisplayName() != null
                    && "Bingo Map".equals(ChatColor.stripColor(existing.getItemMeta().getDisplayName()))) {
                player.sendMessage(ChatColor.YELLOW + "You already have a Bingo map; it updates while held.");
                return;
            }
        }
        World mapWorld = world == null ? player.getWorld() : world;
        MapView view = Bukkit.createMap(mapWorld);
        view.getRenderers().forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.setLocked(true);
        view.addRenderer(new BingoMapRenderer(plugin));
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        meta.setDisplayName(ChatColor.GOLD + "Bingo Map");
        meta.setLore(List.of(ChatColor.GRAY + "A server-rendered 5x5 card", ChatColor.GRAY + "Run /bingo map again to refresh"));
        map.setItemMeta(meta);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(map);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(ChatColor.GREEN + "Bingo map added to your inventory. Hold it to view the card.");
    }

    private void broadcast(String message) {
        Set<UUID> recipients = new HashSet<>(participants);
        recipients.addAll(spectators);
        for (UUID uuid : recipients) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(message);
        }
    }
}
