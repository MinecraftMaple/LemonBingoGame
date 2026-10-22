package dev.Jonsen233.BingoGame.data;

import java.util.UUID;

/** Mutable snapshot of one player's aggregate Bingo statistics. */
public final class PlayerStats {
    private final UUID uuid;
    private String name;
    private long games;
    private long wins;
    private long itemsCollected;
    private long timeouts;
    private long streak;
    private long bestStreak;

    public PlayerStats(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name == null ? "unknown" : name;
    }

    public UUID uuid() {
        return uuid;
    }

    public UUID getUuid() {
        return uuid();
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name();
    }

    public void name(String name) {
        this.name = name == null ? this.name : name;
    }

    public long games() {
        return games;
    }

    public long getGames() {
        return games();
    }

    public void games(long games) {
        this.games = Math.max(0L, games);
    }

    public long wins() {
        return wins;
    }

    public long getWins() {
        return wins();
    }

    public void wins(long wins) {
        this.wins = Math.max(0L, wins);
    }

    public long itemsCollected() {
        return itemsCollected;
    }

    public long getItemsCollected() {
        return itemsCollected();
    }

    public void itemsCollected(long itemsCollected) {
        this.itemsCollected = Math.max(0L, itemsCollected);
    }

    public long timeouts() {
        return timeouts;
    }

    public long getTimeouts() {
        return timeouts();
    }

    public void timeouts(long timeouts) {
        this.timeouts = Math.max(0L, timeouts);
    }

    public long streak() {
        return streak;
    }

    public long getStreak() {
        return streak();
    }

    public void streak(long streak) {
        this.streak = Math.max(0L, streak);
    }

    public long bestStreak() {
        return bestStreak;
    }

    public long getBestStreak() {
        return bestStreak();
    }

    public void bestStreak(long bestStreak) {
        this.bestStreak = Math.max(0L, bestStreak);
    }

    public String winRate() {
        return games == 0L ? "0.0%" : String.format(java.util.Locale.ROOT, "%.1f%%", wins * 100.0 / games);
    }
}
