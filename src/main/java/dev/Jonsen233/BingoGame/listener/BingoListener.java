package dev.Jonsen233.BingoGame.listener;

import dev.Jonsen233.BingoGame.BingoGamePlugin;
import dev.Jonsen233.BingoGame.game.GameState;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;

public final class BingoListener implements Listener {
    private final BingoGamePlugin plugin;

    public BingoListener(BingoGamePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (outsideRound(player)) event.setCancelled(true);
            checkNextTick(player);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (outsideRound(player)) event.setCancelled(true);
            checkNextTick(player);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (outsideRound(event.getPlayer())) event.setCancelled(true);
        checkNextTick(event.getPlayer());
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (outsideRound(event.getPlayer())) event.setCancelled(true);
        checkNextTick(event.getPlayer());
    }

    private boolean outsideRound(Player player) {
        return plugin.getConfig().getBoolean("protection.prevent-outside-world-interaction", true)
                && plugin.game().state() == GameState.RUNNING
                && plugin.game().contains(player)
                && !plugin.game().isRoundWorld(player.getWorld());
    }

    private void checkNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) plugin.game().onItemAcquired(player);
        });
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.game().contains(event.getEntity())) return;
        if (plugin.getConfig().getBoolean("game.keep-inventory-on-death", true)) event.setKeepInventory(true);
    }

    @EventHandler
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (plugin.game().state() != GameState.RUNNING || plugin.game().world() == null) return;
        if (plugin.game().contains(event.getPlayer()) || plugin.game().isSpectator(event.getPlayer())) {
            Location spawn = plugin.game().spawnForWorld(event.getPlayer().getWorld());
            event.setRespawnLocation(spawn == null ? plugin.game().world().getSpawnLocation() : spawn);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getConfig().getBoolean("game.reconnect-players", true)) plugin.game().reconnect(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keep the UUID in the current round so a reconnect can continue; the scan safely skips offline players.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean participant = plugin.game().contains(player);
        boolean spectator = plugin.game().isSpectator(player);
        if ((participant || spectator) && plugin.game().state() == GameState.RUNNING && plugin.game().world() != null && !plugin.game().isRoundWorld(player.getWorld())) {
            if (spectator && player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
            player.teleport(plugin.game().world().getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (plugin.game().state() != GameState.RUNNING || plugin.game().world() == null) return;
        Player player = event.getPlayer();
        boolean participant = plugin.game().contains(player);
        boolean spectator = plugin.game().isSpectator(player);
        if (!participant && !spectator) return;
        Location to = event.getTo();
        if (to != null && !plugin.game().isRoundWorld(to.getWorld())) {
            if (participant && event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
                org.bukkit.World destination = plugin.game().isRoundWorld(player.getWorld())
                        && player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER
                        ? plugin.game().world() : plugin.game().netherWorld();
                if (destination != null) {
                    Location spawn = plugin.game().spawnForWorld(destination);
                    if (spawn != null) event.setTo(spawn);
                    return;
                }
            }
            if (participant && event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
                org.bukkit.World destination = player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END
                        ? plugin.game().world() : plugin.game().endWorld();
                if (destination != null) {
                    Location spawn = plugin.game().spawnForWorld(destination);
                    if (spawn != null) event.setTo(spawn);
                    return;
                }
            }
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + (spectator
                    ? "You cannot leave the Bingo world while spectating. Use /bingo leave to exit."
                    : "You cannot leave the Bingo world during a round."));
        }
        if (spectator && player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMode(PlayerGameModeChangeEvent event) {
        if (plugin.game().state() != GameState.RUNNING) return;
        Player player = event.getPlayer();
        if (plugin.game().isSpectator(player) && event.getNewGameMode() != GameMode.SPECTATOR) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Spectators must remain in spectator mode during a round.");
            return;
        }
        if (plugin.game().contains(player) && event.getNewGameMode() != GameMode.SURVIVAL && !player.hasPermission("bingogame.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if ((!plugin.game().contains(player) && !plugin.game().isSpectator(player)) || plugin.game().state() != GameState.RUNNING) return;
        String command = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT).split("\\s+", 2)[0];
        if (!command.equals("/bingo") && !command.equals("/bingogame")) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + (plugin.game().isSpectator(player)
                    ? "Only Bingo commands are available while spectating. Use /bingo leave to exit."
                    : "Only Bingo commands are available during a round."));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (plugin.game().state() == GameState.RUNNING && plugin.game().isRoundWorld(event.getEntity().getWorld()) && !plugin.getConfig().getBoolean("game.allow-pvp", false)) {
            boolean playerAttack = event.getDamager() instanceof Player || event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player;
            if (event.getEntity() instanceof Player && playerAttack) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (plugin.game().isBoardInventory(event.getView().getTopInventory())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (plugin.game().isBoardInventory(event.getView().getTopInventory())) event.setCancelled(true);
    }

}
