package dev.Jonsen233.BingoGame.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Server-side tab list customisation. The implementation deliberately uses
 * Paper's native tab APIs, so clients do not need a resource pack or mod.
 */
public final class TabManager {
    private static final long DEFAULT_REFRESH_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private final TextPlaceholderResolver placeholders;
    private FileConfiguration config;
    private BukkitTask refreshTask;
    private boolean active;

    public TabManager(JavaPlugin plugin, TextPlaceholderResolver placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        reloadConfig();
    }

    /** Reload tab.yml without requiring a full plugin reload. */
    public void reload() {
        stopTask();
        reloadConfig();
        start();
    }

    public void start() {
        stopTask();
        if (!isEnabled()) {
            clearAll();
            active = false;
            return;
        }
        active = true;
        long interval = Math.max(1L, config.getLong("tab.refresh-interval-ticks", DEFAULT_REFRESH_INTERVAL_TICKS));
        refreshAll();
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
    }

    /** Refreshes the tab header/footer and player display names for one player. */
    public void refresh(Player player) {
        if (!active || !isEnabled() || player == null || !player.isOnline()) return;
        player.sendPlayerListHeaderAndFooter(resolveComponent(player, "tab.header"), resolveComponent(player, "tab.footer"));
        String format = config.getString("tab.player-name-format", "{player}");
        player.playerListName(placeholders.component(placeholders.resolve(player, format)));
    }

    public void refreshAll() {
        if (!active) return;
        if (!isEnabled()) {
            clearAll();
            active = false;
            stopTask();
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player);
    }

    public boolean isEnabled() {
        return config.getBoolean("tab.enabled", true);
    }

    /** Stop updates and restore vanilla tab values for currently online players. */
    public void shutdown() {
        stopTask();
        if (!active) return;
        clearAll();
        active = false;
    }

    private void clearAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            player.playerListName(Component.text(player.getName()));
        }
    }

    private Component resolveComponent(Player player, String path) {
        List<String> lines = config.getStringList(path);
        String text = lines.stream()
                .map(line -> placeholders.resolve(player, line))
                .collect(Collectors.joining("\n"));
        return placeholders.component(text);
    }

    private void stopTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private void reloadConfig() {
        File file = new File(plugin.getDataFolder(), "tab.yml");
        if (!file.isFile()) {
            plugin.saveResource("tab.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }
}
