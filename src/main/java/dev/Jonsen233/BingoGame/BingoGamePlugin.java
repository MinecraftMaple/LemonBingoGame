package dev.Jonsen233.BingoGame;

import dev.Jonsen233.BingoGame.command.BingoCommand;
import dev.Jonsen233.BingoGame.data.MongoStatsService;
import dev.Jonsen233.BingoGame.data.StatsService;
import dev.Jonsen233.BingoGame.game.BingoGame;
import dev.Jonsen233.BingoGame.listener.BingoListener;
import dev.Jonsen233.BingoGame.listener.BingoChatListener;
import dev.Jonsen233.BingoGame.ui.ScoreboardManager;
import dev.Jonsen233.BingoGame.ui.SayingManager;
import dev.Jonsen233.BingoGame.ui.TabManager;
import dev.Jonsen233.BingoGame.ui.TextPlaceholderResolver;
import org.bukkit.ChatColor;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class BingoGamePlugin extends JavaPlugin {
    private BingoGame game;
    private StatsService stats;
    private TabManager tabManager;
    private ScoreboardManager scoreboardManager;
    private SayingManager sayingManager;
    private TextPlaceholderResolver textPlaceholders;
    private org.bukkit.scheduler.BukkitTask uiTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("tab.yml", false);
        game = new BingoGame(this);
        game.cleanupOrphanWorlds();
        stats = new MongoStatsService(this);
        stats.start();
        PluginCommand command = getCommand("bingo");
        if (command != null) {
            BingoCommand executor = new BingoCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(new BingoListener(this), this);
        textPlaceholders = new TextPlaceholderResolver(this);
        tabManager = new TabManager(this, textPlaceholders);
        tabManager.start();
        scoreboardManager = new ScoreboardManager(this, game, textPlaceholders);
        sayingManager = new SayingManager(this, textPlaceholders);
        sayingManager.start();
        getServer().getPluginManager().registerEvents(new BingoChatListener(sayingManager), this);
        uiTask = getServer().getScheduler().runTaskTimer(this, scoreboardManager::refreshAll,
                1L, scoreboardManager.refreshIntervalTicks());
        getServer().getScheduler().runTaskTimer(this, game::tick, 20L, 20L);
        getLogger().info("BingoGame enabled for Paper 1.21.8.");
    }

    @Override
    public void onDisable() {
        if (tabManager != null) tabManager.shutdown();
        if (uiTask != null) { uiTask.cancel(); uiTask = null; }
        if (scoreboardManager != null) scoreboardManager.shutdown();
        if (sayingManager != null) sayingManager.shutdown();
        if (game != null) game.stop(false);
        if (stats != null) stats.close();
    }

    public BingoGame game() { return game; }
    public StatsService stats() { return stats; }
    public TabManager tabManager() { return tabManager; }
    public ScoreboardManager scoreboardManager() { return scoreboardManager; }
    public SayingManager sayingManager() { return sayingManager; }
    public TextPlaceholderResolver textPlaceholders() { return textPlaceholders; }

    public void reloadUiConfigs() {
        if (tabManager != null) tabManager.reload();
        if (scoreboardManager != null) scoreboardManager.reload();
        if (sayingManager != null) sayingManager.reload();
        if (uiTask != null) uiTask.cancel();
        if (scoreboardManager != null) {
            uiTask = getServer().getScheduler().runTaskTimer(this, scoreboardManager::refreshAll,
                    1L, scoreboardManager.refreshIntervalTicks());
        }
    }

    public String msg(String key, Map<String, ?> values) {
        String message = getConfig().getString("messages." + key, key);
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            message = message.replace("%" + entry.getKey() + "%", String.valueOf(entry.getValue()));
        }
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.prefix", "") + message);
    }
}
