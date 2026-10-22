package dev.Jonsen233.BingoGame.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Configurable Paper chat renderer compatible with Printer's saying.yml. */
public final class SayingManager {
    private static final String MESSAGE = "{message}";
    private static final String DEFAULT = "[{luckperms_prefix}]&f{player}: &r{message}";
    private static final String UNCACHED = "&f{message}";
    private final JavaPlugin plugin;
    private final TextPlaceholderResolver placeholders;
    private final Map<UUID, String> formats = new ConcurrentHashMap<>();
    private FileConfiguration config;
    private BukkitTask refreshTask;
    private volatile boolean enabled;
    private volatile String configuredFormat = DEFAULT;

    public SayingManager(JavaPlugin plugin, TextPlaceholderResolver placeholders) {
        this.plugin = plugin;
        this.placeholders = placeholders;
        reloadConfig();
    }

    public void start() {
        stopTask();
        refreshAll();
        long interval = Math.max(1L, config.getLong("saying.refresh-interval-ticks", 20L));
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, interval, interval);
    }

    public void reload() {
        stopTask();
        reloadConfig();
        start();
    }

    public boolean enabled() { return enabled; }

    public void refresh(Player player) {
        if (player == null) return;
        if (!enabled) formats.remove(player.getUniqueId());
        else formats.put(player.getUniqueId(), placeholders.resolve(player, configuredFormat));
    }

    public void remove(UUID uuid) { formats.remove(uuid); }

    /** Returns a fully resolved format prepared by the synchronous refresh task. */
    public String cachedFormat(UUID uuid) {
        return formats.getOrDefault(uuid, UNCACHED);
    }

    /** Renders an already-resolved format; safe to call from Paper's async chat thread. */
    public Component render(String format, Component message) {
        if (!enabled) return null;
        format = format == null ? UNCACHED : format;
        int cursor = 0;
        Component result = Component.empty();
        int index;
        while ((index = format.indexOf(MESSAGE, cursor)) >= 0) {
            result = result.append(placeholders.component(format.substring(cursor, index))).append(message);
            cursor = index + MESSAGE.length();
        }
        return result.append(placeholders.component(format.substring(cursor)));
    }

    public void shutdown() {
        stopTask();
        formats.clear();
    }

    private void refreshAll() {
        enabled = config.getBoolean("saying.enabled", true);
        configuredFormat = config.getString("saying.format", DEFAULT);
        if (configuredFormat == null) configuredFormat = DEFAULT;
        if (!enabled) { formats.clear(); return; }
        formats.keySet().removeIf(uuid -> plugin.getServer().getPlayer(uuid) == null);
        for (Player player : plugin.getServer().getOnlinePlayers()) refresh(player);
    }

    private void reloadConfig() {
        File file = new File(plugin.getDataFolder(), "saying.yml");
        if (!file.isFile()) plugin.saveResource("saying.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    private void stopTask() {
        if (refreshTask != null) { refreshTask.cancel(); refreshTask = null; }
    }
}
