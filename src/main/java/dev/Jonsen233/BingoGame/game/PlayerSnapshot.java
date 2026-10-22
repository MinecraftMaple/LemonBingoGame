package dev.Jonsen233.BingoGame.game;

import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** In-memory round isolation; a snapshot is made before any inventory/world changes. */
public final class PlayerSnapshot {
    private final Location location;
    private final UUID locationWorldId;
    private final String locationWorldName;
    private final ItemStack[] contents;
    private final ItemStack[] armor;
    private final ItemStack offhand;
    private final GameMode gameMode;
    private final double health;
    private final int food;
    private final float saturation;
    private final int level;
    private final float exp;
    private final boolean allowFlight;
    private final boolean flying;
    private final Collection<PotionEffect> effects;

    private PlayerSnapshot(Player player) {
        this.location = player.getLocation().clone();
        World world = location.getWorld();
        this.locationWorldId = world == null ? null : world.getUID();
        this.locationWorldName = world == null ? null : world.getName();
        this.contents = cloneItems(player.getInventory().getStorageContents());
        this.armor = cloneItems(player.getInventory().getArmorContents());
        this.offhand = cloneItem(player.getInventory().getItemInOffHand());
        this.gameMode = player.getGameMode();
        this.health = player.getHealth();
        this.food = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.effects = new ArrayList<>(player.getActivePotionEffects());
    }

    private PlayerSnapshot(Location location, UUID locationWorldId, String locationWorldName,
                           ItemStack[] contents, ItemStack[] armor, ItemStack offhand,
                           GameMode gameMode, double health, int food, float saturation,
                           int level, float exp, boolean allowFlight, boolean flying,
                           Collection<PotionEffect> effects) {
        this.location = location;
        this.locationWorldId = locationWorldId;
        this.locationWorldName = locationWorldName;
        this.contents = contents;
        this.armor = armor;
        this.offhand = offhand;
        this.gameMode = gameMode;
        this.health = health;
        this.food = food;
        this.saturation = saturation;
        this.level = level;
        this.exp = exp;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.effects = effects;
    }

    public static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    /** Converts this snapshot to primitive values understood by Bukkit's YAML configuration. */
    public Map<String, Object> serialize() {
        Map<String, Object> values = new HashMap<>();
        values.put("world", locationWorldName);
        values.put("world-uuid", locationWorldId == null ? null : locationWorldId.toString());
        values.put("x", location.getX());
        values.put("y", location.getY());
        values.put("z", location.getZ());
        values.put("yaw", location.getYaw());
        values.put("pitch", location.getPitch());
        values.put("contents", serializeItems(contents));
        values.put("armor", serializeItems(armor));
        values.put("offhand", offhand == null ? null : offhand.serialize());
        values.put("game-mode", gameMode.name());
        values.put("health", health);
        values.put("food", food);
        values.put("saturation", saturation);
        values.put("level", level);
        values.put("exp", exp);
        values.put("allow-flight", allowFlight);
        values.put("flying", flying);
        List<Map<String, Object>> serializedEffects = new ArrayList<>();
        for (PotionEffect effect : effects) serializedEffects.add(effect.serialize());
        values.put("effects", serializedEffects);
        return values;
    }

    /** Reconstructs a snapshot previously written by {@link #serialize()}. */
    public static PlayerSnapshot deserialize(Map<?, ?> values) {
        if (values == null) throw new IllegalArgumentException("Snapshot values are missing");
        String worldName = stringValue(values.get("world"));
        UUID worldId = parseUuid(values.get("world-uuid"));
        World world = worldId == null ? null : Bukkit.getWorld(worldId);
        if (world == null && worldName != null) world = Bukkit.getWorld(worldName);
        Location location = new Location(world,
                number(values.get("x"), 0D).doubleValue(), number(values.get("y"), 0D).doubleValue(), number(values.get("z"), 0D).doubleValue(),
                number(values.get("yaw"), 0F).floatValue(), number(values.get("pitch"), 0F).floatValue());
        ItemStack[] contents = deserializeItems(values.get("contents"), 36);
        ItemStack[] armor = deserializeItems(values.get("armor"), 4);
        ItemStack offhand = deserializeItem(values.get("offhand"));
        GameMode mode;
        try {
            mode = GameMode.valueOf(stringValue(values.get("game-mode")));
        } catch (Exception ignored) {
            mode = GameMode.SURVIVAL;
        }
        List<PotionEffect> effects = new ArrayList<>();
        Object rawEffects = values.get("effects");
        if (rawEffects instanceof Iterable<?> iterable) {
            for (Object raw : iterable) {
                if (raw instanceof PotionEffect effect) {
                    effects.add(effect);
                    continue;
                }
                if (raw instanceof Map<?, ?> map) {
                    try {
                        effects.add(new PotionEffect(stringMap(map)));
                    } catch (Exception ignored) {
                        // Ignore an effect removed by a newer server version.
                    }
                }
            }
        }
        return new PlayerSnapshot(location, worldId, worldName, contents, armor, offhand, mode,
                number(values.get("health"), 20D).doubleValue(), number(values.get("food"), 20).intValue(),
                number(values.get("saturation"), 5F).floatValue(), number(values.get("level"), 0).intValue(),
                number(values.get("exp"), 0F).floatValue(), booleanValue(values.get("allow-flight")),
                booleanValue(values.get("flying")), effects);
    }

    public void restore(Player player) {
        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneItems(contents));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.getInventory().setItemInOffHand(cloneItem(offhand));
        player.setGameMode(gameMode);
        player.setHealth(Math.min(health, player.getMaxHealth()));
        player.setFoodLevel(food);
        player.setSaturation(saturation);
        player.setLevel(level);
        player.setExp(exp);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        effects.forEach(player::addPotionEffect);
        player.setFireTicks(0);
        World targetWorld = location.getWorld();
        if (targetWorld == null && locationWorldId != null) targetWorld = Bukkit.getWorld(locationWorldId);
        if (targetWorld == null && locationWorldName != null) targetWorld = Bukkit.getWorld(locationWorldName);
        if (targetWorld != null) {
            Location target = location.getWorld() == targetWorld ? location : location.clone();
            target.setWorld(targetWorld);
            player.teleport(target);
        }
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] copy = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) copy[i] = cloneItem(items[i]);
        return copy;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static List<Object> serializeItems(ItemStack[] items) {
        List<Object> serialized = new ArrayList<>(items.length);
        for (ItemStack item : items) serialized.add(item == null ? null : item.serialize());
        return serialized;
    }

    private static ItemStack[] deserializeItems(Object raw, int expectedSize) {
        ItemStack[] result = new ItemStack[expectedSize];
        if (!(raw instanceof Iterable<?> iterable)) return result;
        int index = 0;
        for (Object value : iterable) {
            if (index >= result.length) break;
            result[index++] = deserializeItem(value);
        }
        return result;
    }

    private static ItemStack deserializeItem(Object raw) {
        if (raw instanceof ItemStack item) return item.clone();
        if (!(raw instanceof Map<?, ?> map)) return null;
        try {
            return ItemStack.deserialize(stringMap(map));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Number number(Object value, Number fallback) {
        if (value instanceof Number number) return number;
        try { return value == null ? fallback : Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static UUID parseUuid(Object value) {
        try { return value == null ? null : UUID.fromString(String.valueOf(value)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
