package dev.Jonsen233.BingoGame.game;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Immutable five by five card shared by one round. */
public final class BingoBoard {
    public static final int SIZE = 5;
    public static final int CELLS = SIZE * SIZE;

    /**
     * Default goals. Every entry has a vanilla item form and can be obtained in
     * a normal Overworld without commands or creative mode. The pool is larger
     * than one card so each seed produces a different, fair card.
     */
    private static final List<Material> POOL = List.of(
            Material.OAK_LOG, Material.COBBLESTONE, Material.SAND, Material.GRAVEL,
            Material.CLAY_BALL, Material.FLINT, Material.COAL, Material.CHARCOAL,
            Material.IRON_INGOT, Material.COPPER_INGOT, Material.GOLD_INGOT,
            Material.REDSTONE, Material.LAPIS_LAZULI, Material.DIAMOND,
            Material.EMERALD, Material.QUARTZ, Material.OBSIDIAN, Material.GLASS,
            Material.BREAD, Material.WHEAT, Material.WHEAT_SEEDS, Material.APPLE,
            Material.MELON_SLICE, Material.PUMPKIN, Material.CARROT, Material.POTATO,
            Material.BEETROOT, Material.BEETROOT_SEEDS, Material.CACTUS, Material.SUGAR,
            Material.BONE, Material.BONE_MEAL, Material.STRING, Material.SPIDER_EYE,
            Material.GUNPOWDER, Material.FEATHER, Material.LEATHER, Material.RABBIT_HIDE,
            Material.INK_SAC, Material.BRICK, Material.NETHER_BRICK,
            Material.HONEYCOMB, Material.KELP, Material.BOWL, Material.TORCH,
            Material.CRAFTING_TABLE, Material.FURNACE, Material.CHEST, Material.BUCKET,
            Material.WATER_BUCKET, Material.MILK_BUCKET, Material.BOOK, Material.PAPER,
            Material.ARROW, Material.FIREWORK_ROCKET, Material.SLIME_BALL,
            Material.ENDER_PEARL, Material.BLAZE_ROD, Material.MAGMA_CREAM,
            Material.GHAST_TEAR, Material.SHULKER_SHELL, Material.CHORUS_FRUIT,
            Material.PRISMARINE_SHARD, Material.NAUTILUS_SHELL, Material.TURTLE_SCUTE
    );

    private final List<Material> cells;

    private BingoBoard(List<Material> cells) {
        if (cells == null || cells.size() != CELLS || EnumSet.copyOf(cells).size() != CELLS
                || cells.stream().anyMatch(material -> material == null || !isObtainable(material))) {
            throw new IllegalArgumentException("A bingo board must contain 25 unique materials");
        }
        this.cells = List.copyOf(cells);
    }

    public static BingoBoard random(Random random) {
        return randomFromMaterials(random, POOL);
    }

    /**
     * Creates a card from a configured pool. Unknown, duplicate, non-item, or
     * otherwise unobtainable entries fail fast so a bad config cannot create an
     * unwinnable round. An empty/null list selects the built-in pool.
     */
    public static BingoBoard random(Random random, Collection<String> configuredNames) {
        if (configuredNames == null || configuredNames.isEmpty()) return random(random);
        List<Material> configured = new ArrayList<>();
        for (String name : configuredNames) {
            Material material = parseMaterial(name);
            if (!isObtainable(material)) {
                throw new IllegalArgumentException("Bingo goal is not an obtainable item: " + name);
            }
            if (configured.contains(material)) {
                throw new IllegalArgumentException("Duplicate Bingo goal: " + name);
            }
            configured.add(material);
        }
        return randomFromMaterials(random, configured);
    }

    /** Creates a configured card while enforcing the dimensions enabled for the round. */
    public static BingoBoard random(Random random, Collection<String> configuredNames,
                                    boolean netherEnabled, boolean endEnabled) {
        if (configuredNames == null || configuredNames.isEmpty()) {
            return randomFromMaterials(random, defaultPool(netherEnabled, endEnabled));
        }
        List<Material> configured = new ArrayList<>();
        for (String name : configuredNames) {
            Material material = parseMaterial(name);
            if (!isObtainable(material) || !isAvailableInDimensions(material, netherEnabled, endEnabled)) {
                throw new IllegalArgumentException("Bingo goal is unavailable in the enabled round dimensions: " + name);
            }
            if (configured.contains(material)) {
                throw new IllegalArgumentException("Duplicate Bingo goal: " + name);
            }
            configured.add(material);
        }
        return randomFromMaterials(random, configured);
    }

    /** Selects and shuffles 25 unique goals from a material pool. */
    public static BingoBoard randomFromMaterials(Random random, Collection<Material> pool) {
        if (random == null) throw new IllegalArgumentException("Random source cannot be null");
        if (pool == null || pool.size() < CELLS) {
            throw new IllegalArgumentException("Bingo goal pool must contain at least " + CELLS + " entries");
        }
        EnumSet<Material> unique = EnumSet.noneOf(Material.class);
        for (Material material : pool) {
            if (!isObtainable(material)) {
                throw new IllegalArgumentException("Bingo goal is not an obtainable item: " + material);
            }
            unique.add(material);
        }
        if (unique.size() < CELLS) {
            throw new IllegalArgumentException("Bingo goal pool must contain at least " + CELLS + " unique entries");
        }
        List<Material> shuffled = new ArrayList<>(unique);
        Collections.shuffle(shuffled, random);
        return new BingoBoard(shuffled.subList(0, CELLS));
    }

    /** Returns the built-in pool for admin UIs and config validation. */
    public static List<Material> defaultPool() {
        return POOL;
    }

    /**
     * Filters the built-in pool to the dimensions that the current round owns.
     * The default card contains Nether and End goals, so this keeps a round
     * winnable when an administrator intentionally disables either dimension.
     */
    public static List<Material> defaultPool(boolean netherEnabled, boolean endEnabled) {
        return POOL.stream()
                .filter(material -> isAvailableInDimensions(material, netherEnabled, endEnabled))
                .toList();
    }

    /** Returns whether an item can be obtained without entering a disabled dimension. */
    public static boolean isAvailableInDimensions(Material material, boolean netherEnabled, boolean endEnabled) {
        if (material == null) return false;
        if (!netherEnabled && requiresNether(material)) return false;
        if (!endEnabled && requiresEnd(material)) return false;
        return true;
    }

    private static boolean requiresNether(Material material) {
        return switch (material) {
            case QUARTZ, NETHER_BRICK, BLAZE_ROD, MAGMA_CREAM, GHAST_TEAR -> true;
            default -> false;
        };
    }

    private static boolean requiresEnd(Material material) {
        return switch (material) {
            case SHULKER_SHELL, CHORUS_FRUIT -> true;
            default -> false;
        };
    }

    public static BingoBoard fromNames(List<String> names) {
        if (names == null) throw new IllegalArgumentException("Bingo goal names cannot be null");
        List<Material> materials = new ArrayList<>();
        for (String name : names) {
            Material material = parseMaterial(name);
            if (!POOL.contains(material)) {
                throw new IllegalArgumentException("Unknown or unavailable Bingo goal: " + name);
            }
            materials.add(material);
        }
        return new BingoBoard(materials);
    }

    public List<Material> materials() {
        return cells;
    }

    public Material material(int index) {
        if (index < 0 || index >= CELLS) throw new IndexOutOfBoundsException(index);
        return cells.get(index);
    }

    public List<String> names() {
        return cells.stream().map(Material::name).toList();
    }

    public boolean hasLine(Set<Integer> collected) {
        return !winningLines(collected).isEmpty();
    }

    public boolean isComplete(Set<Integer> collected) {
        return collected.size() >= CELLS;
    }

    public int progress(Set<Integer> collected) {
        return Math.min(CELLS, collected.size());
    }

    /**
     * Returns completed line ids: rows 0-4, columns 5-9, main diagonal 10 and
     * anti-diagonal 11. The ids make it possible for a GUI to highlight every
     * winning cell without duplicating board geometry.
     */
    public List<Integer> winningLines(Set<Integer> collected) {
        List<Integer> lines = new ArrayList<>();
        for (int row = 0; row < SIZE; row++) {
            boolean complete = true;
            for (int col = 0; col < SIZE; col++) complete &= collected.contains(row * SIZE + col);
            if (complete) lines.add(row);
        }
        for (int col = 0; col < SIZE; col++) {
            boolean complete = true;
            for (int row = 0; row < SIZE; row++) complete &= collected.contains(row * SIZE + col);
            if (complete) lines.add(SIZE + col);
        }
        boolean diagonal = true;
        boolean reverseDiagonal = true;
        for (int i = 0; i < SIZE; i++) {
            diagonal &= collected.contains(i * SIZE + i);
            reverseDiagonal &= collected.contains(i * SIZE + (SIZE - i - 1));
        }
        if (diagonal) lines.add(SIZE * 2);
        if (reverseDiagonal) lines.add(SIZE * 2 + 1);
        return lines;
    }

    /** Number of completed rows, columns, and diagonals. */
    public int completedLineCount(Set<Integer> collected) {
        return winningLines(collected).size();
    }

    /** Returns true for a valid vanilla item that can be placed in an inventory. */
    public static boolean isObtainable(Material material) {
        if (material == null) return false;
        // Keep board unit tests usable without a running Paper registry. These
        // names are the non-item pseudo blocks that can never be a goal.
        String name = material.name();
        if (name.equals("AIR") || name.endsWith("_AIR") || name.equals("CAVE_AIR")
                || name.equals("VOID_AIR") || name.equals("WATER") || name.equals("LAVA")
                || name.equals("FIRE") || name.equals("SOUL_FIRE") || name.equals("BUBBLE_COLUMN")) {
            return false;
        }
        try {
            return material.isItem();
        } catch (IllegalStateException | LinkageError ignored) {
            // Bukkit's registry is initialized by a live server. At build/test
            // time the material name checks above are the safe fallback.
            return true;
        }
    }

    private static Material parseMaterial(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Bingo goal cannot be blank");
        String normalized = name.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("MINECRAFT:")) normalized = normalized.substring("MINECRAFT:".length());
        Material material = Material.matchMaterial(normalized);
        if (material == null) throw new IllegalArgumentException("Unknown Bingo goal: " + name);
        return material;
    }

    public static String displayName(Material material) {
        String[] words = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
