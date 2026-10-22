package dev.Jonsen233.BingoGame.game;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class BingoBoardHolder implements InventoryHolder {
    private Inventory inventory;

    public void inventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public Inventory getInventory() { return inventory; }
}
