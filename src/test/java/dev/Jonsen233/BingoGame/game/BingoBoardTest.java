package dev.Jonsen233.BingoGame.game;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BingoBoardTest {
    private BingoBoard board() {
        return BingoBoard.fromNames(List.of(
                "OAK_LOG", "COBBLESTONE", "SAND", "GRAVEL", "CLAY_BALL",
                "FLINT", "COAL", "IRON_INGOT", "GOLD_INGOT", "REDSTONE",
                "LAPIS_LAZULI", "DIAMOND", "OBSIDIAN", "GLASS", "BREAD",
                "APPLE", "MELON_SLICE", "PUMPKIN", "CACTUS", "SUGAR",
                "BONE", "STRING", "FEATHER", "LEATHER", "INK_SAC"));
    }

    @Test
    void lineWinAcceptsRowsColumnsAndBothDiagonals() {
        BingoBoard board = board();
        assertTrue(board.hasLine(Set.of(0, 1, 2, 3, 4)));
        assertTrue(board.hasLine(Set.of(0, 5, 10, 15, 20)));
        assertTrue(board.hasLine(Set.of(0, 6, 12, 18, 24)));
        assertTrue(board.hasLine(Set.of(4, 8, 12, 16, 20)));
        assertFalse(board.hasLine(Set.of(0, 1, 2, 3, 5)));
    }

    @Test
    void fullCardRequiresAllTwentyFiveUniqueCells() {
        BingoBoard board = board();
        Set<Integer> cells = new HashSet<>();
        for (int i = 0; i < BingoBoard.CELLS - 1; i++) cells.add(i);
        assertFalse(board.isComplete(cells));
        cells.add(BingoBoard.CELLS - 1);
        assertTrue(board.isComplete(cells));
        assertEquals(BingoBoard.CELLS, board.materials().size());
    }

    @Test
    void cardMaterialsAreDistinctAndItems() {
        BingoBoard board = BingoBoard.random(new java.util.Random(1234));
        assertEquals(BingoBoard.CELLS, board.materials().stream().distinct().count());
        assertTrue(board.materials().stream().allMatch(BingoBoard::isObtainable));
    }

    @Test
    void configuredPoolAcceptsNamespacedNamesAndSelectsTwentyFive() {
        List<String> pool = BingoBoard.defaultPool().stream().map(material -> "minecraft:" + material.name()).toList();
        BingoBoard board = BingoBoard.random(new java.util.Random(55), pool);
        assertEquals(BingoBoard.CELLS, board.materials().size());
        assertTrue(board.materials().stream().allMatch(poolMaterial -> pool.contains("minecraft:" + poolMaterial.name())));
    }

    @Test
    void winningLinesIncludeRowsColumnsAndBothDiagonals() {
        BingoBoard board = BingoBoard.random(new java.util.Random(99));
        Set<Integer> all = new HashSet<>();
        for (int i = 0; i < BingoBoard.CELLS; i++) all.add(i);
        assertEquals(12, board.completedLineCount(all));
        assertEquals(List.of(0, 5, 10), board.winningLines(Set.of(0, 1, 2, 3, 4, 5, 6, 10, 12, 15, 18, 20, 24)));
    }

    @Test
    void disabledDimensionsNeverProduceUnreachableBuiltInGoals() {
        List<org.bukkit.Material> pool = BingoBoard.defaultPool(false, false);
        assertTrue(pool.size() >= BingoBoard.CELLS);
        assertFalse(pool.contains(org.bukkit.Material.BLAZE_ROD));
        assertFalse(pool.contains(org.bukkit.Material.SHULKER_SHELL));
        assertTrue(BingoBoard.randomFromMaterials(new java.util.Random(7), pool).materials().stream()
                .allMatch(material -> BingoBoard.isAvailableInDimensions(material, false, false)));
    }

    @Test
    void configuredGoalsFromDisabledDimensionsAreRejected() {
        List<String> pool = new java.util.ArrayList<>();
        for (org.bukkit.Material material : BingoBoard.defaultPool(false, false)) pool.add(material.name());
        pool.set(0, "BLAZE_ROD");
        assertThrows(IllegalArgumentException.class,
                () -> BingoBoard.random(new java.util.Random(9), pool, false, false));
    }
}
