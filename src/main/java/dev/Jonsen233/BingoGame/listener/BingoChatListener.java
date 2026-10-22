package dev.Jonsen233.BingoGame.listener;

import dev.Jonsen233.BingoGame.ui.SayingManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Applies saying.yml to Paper chat while keeping placeholder work cached on the main thread. */
public final class BingoChatListener implements Listener {
    private final SayingManager saying;

    public BingoChatListener(SayingManager saying) { this.saying = saying; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!saying.enabled()) return;
        String format = saying.cachedFormat(event.getPlayer().getUniqueId());
        event.renderer((source, displayName, message, viewer) -> saying.render(format, message));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { saying.refresh(event.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { saying.remove(event.getPlayer().getUniqueId()); }
}
