package dev.Jonsen233.BingoGame.command;

import dev.Jonsen233.BingoGame.BingoGamePlugin;
import dev.Jonsen233.BingoGame.data.PlayerStats;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BingoCommand implements CommandExecutor, TabCompleter {
    private final BingoGamePlugin plugin;

    public BingoCommand(BingoGamePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            sender.sendMessage(ChatColor.GOLD + "/bingo join, leave, spectate, board, map, start, stop, stats, top, reload");
            return true;
        }
        if (sub.equals("stats")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            plugin.stats().getOrCreate(player.getUniqueId(), player.getName()).thenAccept(stats -> plugin.getServer().getScheduler().runTask(plugin, () -> showStats(player, stats)));
            return true;
        }
        if (sub.equals("top")) {
            plugin.stats().top("wins", 10).thenAccept(stats -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                sender.sendMessage(ChatColor.GOLD + "Bingo wins leaderboard:");
                int rank = 1;
                for (PlayerStats stat : stats) sender.sendMessage(ChatColor.YELLOW + "#" + rank++ + " " + stat.name() + " " + stat.wins());
            }));
            return true;
        }
        if (sub.equals("reload")) {
            if (!sender.hasPermission("bingogame.admin")) { sender.sendMessage(plugin.msg("no-permission", Map.of())); return true; }
            plugin.reloadConfig();
            plugin.reloadUiConfigs();
            sender.sendMessage(ChatColor.GREEN + "Bingo configuration reloaded."); return true;
        }
        if (sub.equals("start") || sub.equals("stop")) {
            if (!sender.hasPermission("bingogame.admin")) { sender.sendMessage(plugin.msg("no-permission", Map.of())); return true; }
            if (sub.equals("start")) plugin.game().forceStart(); else plugin.game().stop(true);
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        switch (sub) {
            case "join" -> {
                if (plugin.game().isSpectator(player)) {
                    player.sendMessage(ChatColor.YELLOW + "Leave spectator mode before joining the next Bingo round.");
                } else if (!plugin.game().join(player)) {
                    player.sendMessage(plugin.msg("already-joined", Map.of()));
                }
            }
            case "leave" -> {
                if (plugin.game().leave(player)) player.sendMessage(plugin.msg("left", Map.of()));
                else player.sendMessage(plugin.msg("not-joined", Map.of()));
            }
            case "spectate", "watch" -> {
                if (plugin.game().contains(player)) {
                    player.sendMessage(ChatColor.YELLOW + "You are already playing in this Bingo round.");
                } else if (plugin.game().isSpectator(player)) {
                    player.sendMessage(plugin.msg("spectate-already", Map.of()));
                } else if (plugin.game().spectate(player)) {
                    player.sendMessage(plugin.msg("spectate-start", Map.of()));
                } else {
                    player.sendMessage(plugin.msg("spectate-unavailable", Map.of()));
                }
            }
            case "board", "card" -> plugin.game().openBoard(player);
            case "map" -> plugin.game().giveMap(player);
            default -> player.sendMessage(ChatColor.GOLD + "/bingo join, leave, spectate, board, map, start, stop, stats, top, reload");
        }
        return true;
    }

    private void showStats(Player player, PlayerStats stats) {
        player.sendMessage(plugin.msg("stats", Map.of("player", stats.name(), "games", stats.games(), "wins", stats.wins(), "winrate", stats.winRate(), "beststreak", stats.bestStreak())));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : List.of("join", "leave", "spectate", "watch", "board", "map", "start", "stop", "stats", "top", "reload")) if (value.startsWith(args[0].toLowerCase(Locale.ROOT))) result.add(value);
        return result;
    }
}
