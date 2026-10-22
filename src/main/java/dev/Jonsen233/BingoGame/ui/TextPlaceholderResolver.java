package dev.Jonsen233.BingoGame.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

/** Resolves built-in, LuckPerms, and optional PlaceholderAPI text variables. */
public final class TextPlaceholderResolver {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private final Method papiMethod;
    private final Class<?> luckPermsClass;
    private boolean papiErrorLogged;
    private boolean luckPermsErrorLogged;

    public TextPlaceholderResolver(JavaPlugin plugin) {
        this.plugin = plugin;
        this.papiMethod = findPapiMethod();
        this.luckPermsClass = findClass("net.luckperms.api.LuckPerms");
    }

    public String resolve(Player player, String text) {
        String result = text == null ? "" : text;
        String playerName = player == null ? "" : player.getName();
        LuckPermsValues luckPerms = player == null ? LuckPermsValues.EMPTY : luckPermsValues(player);
        String prefix = luckPerms.prefix();
        result = result
                .replace("{player}", playerName)
                .replace("{player_name}", playerName)
                .replace("%player%", playerName)
                .replace("%player_name%", playerName)
                .replace("{luckperms_prefix}", prefix)
                .replace("%luckperms_prefix%", prefix)
                .replace("{luckperms_suffix}", luckPerms.suffix())
                .replace("%luckperms_suffix%", luckPerms.suffix())
                .replace("{luckperms_group}", luckPerms.primaryGroup())
                .replace("%luckperms_group%", luckPerms.primaryGroup())
                .replace("{luckperms_primary_group_name}", luckPerms.primaryGroup())
                .replace("%luckperms_primary_group_name%", luckPerms.primaryGroup())
                .replace("{online_players}", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                .replace("%online_players%", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                .replace("{max_players}", String.valueOf(plugin.getServer().getMaxPlayers()))
                .replace("%max_players%", String.valueOf(plugin.getServer().getMaxPlayers()))
                .replace("{server}", plugin.getServer().getName())
                .replace("%server%", plugin.getServer().getName());
        if (player == null || papiMethod == null) return result;
        try {
            Object resolved = papiMethod.invoke(null, (OfflinePlayer) player, result);
            return resolved instanceof String value ? value : result;
        } catch (ReflectiveOperationException exception) {
            if (!papiErrorLogged) {
                papiErrorLogged = true;
                plugin.getLogger().warning("Unable to resolve PlaceholderAPI text: " + exception.getMessage());
            }
            return result;
        }
    }

    public Component component(String text) {
        String legacy = ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
        return LEGACY.deserialize(legacy);
    }

    private LuckPermsValues luckPermsValues(Player player) {
        if (luckPermsClass == null || !plugin.getServer().getPluginManager().isPluginEnabled("LuckPerms")) return LuckPermsValues.EMPTY;
        try {
            Object registration = plugin.getServer().getServicesManager().getRegistration(luckPermsClass);
            if (registration == null) return LuckPermsValues.EMPTY;
            Object api = registration.getClass().getMethod("getProvider").invoke(registration);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUniqueId());
            if (user == null) return LuckPermsValues.EMPTY;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
            Object suffix = metaData.getClass().getMethod("getSuffix").invoke(metaData);
            Object group = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return new LuckPermsValues(value(prefix), value(suffix), value(group));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!luckPermsErrorLogged) {
                luckPermsErrorLogged = true;
                plugin.getLogger().warning("Unable to resolve LuckPerms prefix: " + exception.getMessage());
            }
            return LuckPermsValues.EMPTY;
        }
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record LuckPermsValues(String prefix, String suffix, String primaryGroup) {
        private static final LuckPermsValues EMPTY = new LuckPermsValues("", "", "");
    }

    private Method findPapiMethod() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) return null;
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return api.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException exception) {
            if (!papiErrorLogged) {
                papiErrorLogged = true;
                plugin.getLogger().warning("Unable to load PlaceholderAPI integration: " + exception.getMessage());
            }
            return null;
        }
    }

    private Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
