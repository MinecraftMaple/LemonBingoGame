package dev.Jonsen233.BingoGame.data;

import dev.Jonsen233.BingoGame.game.PlayerSnapshot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Durable storage for player state captured before entering a Bingo round. */
public final class SnapshotStore {
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public SnapshotStore(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Unable to create plugin data folder for snapshots.");
        }
        this.file = new File(plugin.getDataFolder(), "snapshots.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Map<UUID, PlayerSnapshot> load() {
        Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("snapshots");
        if (section == null) return snapshots;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection values = section.getConfigurationSection(key);
                if (values == null) continue;
                snapshots.put(uuid, PlayerSnapshot.deserialize(values.getValues(false)));
            } catch (Exception exception) {
                plugin.getLogger().log(Level.WARNING, "Ignoring invalid player snapshot " + key, exception);
            }
        }
        return snapshots;
    }

    public void put(UUID uuid, PlayerSnapshot snapshot) {
        yaml.set("snapshots." + uuid, snapshot.serialize());
        save();
    }

    public void remove(UUID uuid) {
        yaml.set("snapshots." + uuid, null);
        save();
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Unable to save player snapshots", exception);
        }
    }
}
