package dev.Jonsen233.BingoGame.ui;

import dev.Jonsen233.BingoGame.BingoGamePlugin;
import dev.Jonsen233.BingoGame.game.BingoGame;
import dev.Jonsen233.BingoGame.game.GameState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Native Paper sidebar implementation compatible with Printer's scoreboard.yml. */
public final class ScoreboardManager {
    private static final int MAX_LINES = 15;
    private final BingoGamePlugin plugin;
    private final BingoGame game;
    private final TextPlaceholderResolver placeholders;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private FileConfiguration config;

    public ScoreboardManager(BingoGamePlugin plugin, BingoGame game, TextPlaceholderResolver placeholders) {
        this.plugin = plugin;
        this.game = game;
        this.placeholders = placeholders;
        reloadConfig();
    }

    public void reload() {
        reloadConfig();
        clearAndRefresh();
    }

    public boolean enabled() { return config.getBoolean("scoreboard.enabled", true); }

    public long refreshIntervalTicks() {
        return Math.max(1L, config.getLong("scoreboard.refresh-interval-ticks", 20L));
    }

    public void refreshAll() {
        Set<UUID> visible = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            visible.add(player.getUniqueId());
            refresh(player);
        }
        boards.keySet().removeIf(uuid -> !visible.contains(uuid));
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!enabled()) { clear(player); return; }
        List<RoundRank> ranking = roundRanking();
        List<String> raw = linesFor(player);
        List<String> lines = new ArrayList<>();
        boolean running = game.state() == GameState.RUNNING && game.contains(player);
        boolean hasMarker = raw.stream().anyMatch(line -> "{leaderboard}".equals(line.trim()));
        boolean inserted = false;
        for (String line : raw) {
            if ("{leaderboard}".equals(line.trim())) {
                lines.addAll(leaderboardLines(player, ranking));
                inserted = true;
            } else {
                lines.add(line);
                // Keep Printer's convenient implicit insertion behavior for
                // running cards that place {wall} before their leaderboard.
                if (running && !hasMarker && !inserted && line.contains("{wall}")) {
                    lines.addAll(leaderboardLines(player, ranking));
                    inserted = true;
                }
            }
        }
        if (running && !inserted && !hasMarker) {
            lines.addAll(leaderboardLines(player, ranking));
        }
        lines = lines.subList(0, Math.min(MAX_LINES, lines.size()));
        Scoreboard scoreboard = Bukkit.getScoreboardManager() == null
                ? null : Bukkit.getScoreboardManager().getNewScoreboard();
        if (scoreboard == null) return;
        Objective objective = scoreboard.registerNewObjective("bingo", "dummy", text(config.getString("scoreboard.title", "&6&lBINGO")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        int score = lines.size();
        for (int index = 0; index < lines.size(); index++) {
            String value = text(trim(resolve(lines.get(index), player, ranking)));
            if (value.isEmpty()) value = " ";
            String entry = uniqueEntry(value, index);
            objective.getScore(entry).setScore(score--);
        }
        boards.put(player.getUniqueId(), scoreboard);
        player.setScoreboard(scoreboard);
    }

    public void clearAndRefresh() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        boards.clear();
        if (enabled()) refreshAll();
    }

    public void shutdown() {
        for (Player player : Bukkit.getOnlinePlayers()) clear(player);
        boards.clear();
    }

    private void clear(Player player) {
        boards.remove(player.getUniqueId());
        if (Bukkit.getScoreboardManager() != null) player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    private List<String> linesFor(Player player) {
        String section;
        if (game.state() == GameState.RUNNING) {
            section = game.contains(player) ? "scoreboard.running.lines" : "scoreboard.spectator.lines";
        } else {
            section = "scoreboard.waiting.lines";
        }
        List<String> lines = config.getStringList(section);
        return lines.isEmpty() ? List.of("&6&lBINGO", "&7Status: &f{status}") : lines;
    }

    private List<String> leaderboardLines(Player player, List<RoundRank> ranking) {
        List<String> lines = new ArrayList<>();
        for (int rank = 1; rank <= 3; rank++) {
            String path = "scoreboard.running.leaderboard.rank-" + rank + "-line";
            lines.add(config.getString(path, "&e" + rank + ". &f{leader_" + rank + "_player} &a{leader_" + rank + "_score}"));
        }
        lines.add(config.getString("scoreboard.running.leaderboard.separator-line", "&7....."));
        if (rankOf(player, ranking) > 3) {
            lines.add(config.getString("scoreboard.running.leaderboard.self-line",
                    "&e{player_rank}. &f{player} &a{score}"));
        }
        return lines;
    }

    private String resolve(String line, Player player, List<RoundRank> ranking) {
        // Printer's scoreboard.yml treats {server} as a configurable label,
        // while the shared resolver's server placeholder is the Bukkit name.
        // Resolve this one first so the scoreboard setting wins.
        String serverName = config.getString("scoreboard.server", plugin.getServer().getName());
        String result = placeholders.resolve(player,
                line.replace("{server}", serverName).replace("%server%", serverName));
        result = result.replace("{date}", formattedDate())
                .replace("{status}", statusText())
                .replace("{current_players}", String.valueOf(game.playerCount()))
                .replace("{min_players}", String.valueOf(plugin.getConfig().getInt("game.min-players", 2)))
                .replace("{max_players}", String.valueOf(plugin.getConfig().getInt("game.max-players", 16)))
                .replace("{start_time}", countdownText())
                .replace("{round}", game.state().name())
                .replace("{wall}", String.valueOf(game.progress(player)))
                .replace("{walls_per_round}", "25")
                .replace("{score}", String.valueOf(game.progress(player)))
                .replace("{progress}", String.valueOf(game.progress(player)))
                .replace("{remaining_seconds}", String.valueOf(game.remainingSeconds()))
                .replace("{time}", formatTime(game.remainingSeconds()))
                .replace("{active_players}", String.valueOf(activePlayerCount()))
                .replace("{wins}", String.valueOf(plugin.stats() == null ? 0L
                        : plugin.stats().cached(player.getUniqueId(), player.getName()).wins()))
                .replace("{player}", player.getName());
        for (int rank = 1; rank <= 3; rank++) {
            RoundRank stats = rank <= ranking.size() ? ranking.get(rank - 1) : null;
            result = result.replace("{leader_" + rank + "_player}", stats == null ? config.getString("scoreboard.running.leaderboard.missing-player", "-") : stats.name())
                    .replace("{leader_" + rank + "_score}", stats == null ? "0" : String.valueOf(stats.progress()));
        }
        int ownRank = rankOf(player, ranking);
        return result.replace("{player_rank}", ownRank == 0 ? "-" : String.valueOf(ownRank));
    }

    private String statusText() {
        String key = game.state().name().toLowerCase(Locale.ROOT);
        return config.getString("scoreboard.status." + key, key);
    }

    private String countdownText() {
        if (game.state() == GameState.COUNTDOWN) return String.valueOf(game.countdownSeconds());
        return game.state() == GameState.RUNNING
                ? formatTime(game.remainingSeconds())
                : config.getString("scoreboard.waiting.waiting-text", "Waiting...");
    }

    private String formattedDate() {
        String pattern = config.getString("scoreboard.date-format", "MM/dd/yy");
        try { return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)); }
        catch (IllegalArgumentException ignored) { return LocalDate.now().format(DateTimeFormatter.ofPattern("MM/dd/yy", Locale.ENGLISH)); }
    }

    private String formatTime(long seconds) { return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60); }

    private String trim(String value) {
        int max = Math.max(16, config.getInt("scoreboard.max-line-length", 64));
        if (value.length() <= max) return value;
        String result = value.substring(0, max);
        return result.endsWith("&") ? result.substring(0, result.length() - 1) : result;
    }

    private String text(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }

    private String uniqueEntry(String value, int index) {
        return value + ChatColor.COLOR_CHAR + Integer.toHexString(index % 16);
    }

    private void reloadConfig() {
        File file = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!file.isFile()) plugin.saveResource("scoreboard.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    private int activePlayerCount() {
        int count = 0;
        for (UUID uuid : game.participants()) {
            if (Bukkit.getPlayer(uuid) != null) count++;
        }
        return count;
    }

    /** Returns the live progress ranking for the current Bingo round. */
    private List<RoundRank> roundRanking() {
        List<RoundRank> ranking = new ArrayList<>();
        for (UUID uuid : game.participants()) {
            Player participant = Bukkit.getPlayer(uuid);
            String name = participant == null ? uuid.toString().substring(0, 8) : participant.getName();
            ranking.add(new RoundRank(uuid, name, game.progress(uuid)));
        }
        ranking.sort(Comparator.comparingInt(RoundRank::progress).reversed()
                .thenComparing(RoundRank::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(rank -> rank.uuid().toString()));
        return ranking;
    }

    private int rankOf(Player player, List<RoundRank> ranking) {
        for (int index = 0; index < ranking.size(); index++) {
            if (ranking.get(index).uuid().equals(player.getUniqueId())) return index + 1;
        }
        return 0;
    }

    private record RoundRank(UUID uuid, String name, int progress) { }
}
