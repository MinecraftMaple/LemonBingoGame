package dev.Jonsen233.BingoGame.data;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Persistent player statistics used by the Bingo game and /bingo stats. */
public interface StatsService extends AutoCloseable {
    /** Starts the persistence connection without blocking the server thread. */
    void start();

    @Override
    void close();

    boolean available();

    CompletableFuture<PlayerStats> getOrCreate(UUID uuid, String name);

    /** Returns the current in-memory value, creating an empty value when needed. */
    PlayerStats cached(UUID uuid, String name);

    CompletableFuture<Optional<PlayerStats>> findByName(String name);

    /** Returns the top players for a whitelisted numeric field. */
    CompletableFuture<List<PlayerStats>> top(String field, int limit);

    /** Records one completed round. */
    void recordGame(UUID uuid, String name, boolean winner, long itemsCollected, boolean timedOut);

    /** Compatibility helper for game controllers that only track completion count. */
    default void recordRound(UUID uuid, String name, boolean winner, int itemsCollected) {
        recordGame(uuid, name, winner, itemsCollected, false);
    }
}
