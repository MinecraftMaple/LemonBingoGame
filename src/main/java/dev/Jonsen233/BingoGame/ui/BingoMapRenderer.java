package dev.Jonsen233.BingoGame.ui;

import dev.Jonsen233.BingoGame.BingoGamePlugin;
import dev.Jonsen233.BingoGame.game.BingoBoard;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;

import java.awt.Color;

/**
 * Server-rendered vanilla map card. It uses only the normal map protocol, so
 * players do not need a mod or resource pack. A new map can be requested with
 * /bingo map at any time to refresh the card in the inventory.
 */
public final class BingoMapRenderer extends MapRenderer {
    private final BingoGamePlugin plugin;
    private final MapFont font = MinecraftFont.Font;

    public BingoMapRenderer(BingoGamePlugin plugin) {
        super(true);
        this.plugin = plugin;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixelColor(x, y, Color.WHITE);
            }
        }
        canvas.drawText(2, 2, font, "BINGO 5x5");
        if ((!plugin.game().contains(player) && !plugin.game().isSpectator(player)) || plugin.game().board() == null) {
            canvas.drawText(2, 18, font, "Join a game");
            return;
        }
        BingoBoard board = plugin.game().board();
        canvas.drawText(2, 13, font, "Line  Time " + plugin.game().remainingSeconds() + "s");
        int top = 25;
        int cellWidth = 25;
        int cellHeight = 18;
        for (int index = 0; index < BingoBoard.CELLS; index++) {
            int row = index / BingoBoard.SIZE;
            int column = index % BingoBoard.SIZE;
            int x = 2 + column * cellWidth;
            int y = top + row * cellHeight;
            boolean complete = plugin.game().hasCollected(player, index);
            Material material = board.material(index);
            Color fill = complete ? new Color(110, 190, 110) : new Color(225, 225, 225);
            for (int px = x; px < x + cellWidth - 1; px++) {
                for (int py = y; py < y + cellHeight - 1; py++) canvas.setPixelColor(px, py, fill);
            }
            canvas.drawText(x + 2, y + 4, font, (complete ? "X" : abbreviation(material)));
        }
        canvas.drawText(2, 117, font, "Green=done  /bingo board");
    }

    private String abbreviation(Material material) {
        String name = material.name().replace('_', ' ');
        StringBuilder compact = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) compact.append(word.charAt(0));
            if (compact.length() == 3) break;
        }
        if (compact.length() < 3) compact.append(name.replace(" ", ""), 0, Math.min(3 - compact.length(), name.replace(" ", "").length()));
        return compact.toString();
    }
}
