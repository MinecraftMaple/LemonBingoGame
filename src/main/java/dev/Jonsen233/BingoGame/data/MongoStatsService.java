package dev.Jonsen233.BingoGame.data;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MongoDB-backed statistics with a memory-only fallback.
 *
 * Mongo driver calls are kept on one daemon executor. This prevents a slow or
 * unavailable database from blocking the Paper tick thread and also serializes
 * streak updates so two rounds cannot overwrite one another.
 */
public final class MongoStatsService implements StatsService {
    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "games", "wins", "itemsCollected", "timeouts", "streak", "bestStreak");

    private final JavaPlugin plugin;
    private final ExecutorService executor;
    private final ConcurrentHashMap<UUID, PlayerStats> cache = new ConcurrentHashMap<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean failureReported = new AtomicBoolean();
    private volatile MongoClient client;
    private volatile MongoCollection<Document> players;
    private volatile MongoCollection<Document> games;
    private volatile boolean available;
    private volatile boolean closing;

    public MongoStatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BingoGame-Mongo");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("mongodb.enabled", true)) {
            plugin.getLogger().warning("MongoDB is disabled; Bingo statistics will remain in memory.");
            return;
        }
        executor.execute(this::connect);
    }

    private void connect() {
        if (closing) {
            return;
        }
        try {
            String uri = plugin.getConfig().getString("mongodb.connection-string", "mongodb://localhost:27017");
            long timeout = Math.max(250L, plugin.getConfig().getLong("mongodb.server-selection-timeout-ms", 2000L));
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    .applyToClusterSettings(builder -> builder.serverSelectionTimeout(timeout, TimeUnit.MILLISECONDS))
                    .build();
            MongoClient created = MongoClients.create(settings);
            MongoDatabase database = created.getDatabase(plugin.getConfig().getString("mongodb.database", "BingoGame"));
            String playerName = plugin.getConfig().getString("mongodb.player-collection", "player_stats");
            String gameName = plugin.getConfig().getString("mongodb.game-collection", "games");
            ensureCollection(database, playerName);
            ensureCollection(database, gameName);
            MongoCollection<Document> playerCollection = database.getCollection(playerName);
            if (plugin.getConfig().getBoolean("mongodb.ensure-indexes", true)) {
                ensureIndexes(playerCollection);
            }
            client = created;
            players = playerCollection;
            games = database.getCollection(gameName);
            available = true;
            plugin.getLogger().info("BingoGame MongoDB statistics connected.");
        } catch (Exception exception) {
            available = false;
            closeClient();
            reportFailure("MongoDB connection failed; continuing with memory-only statistics", exception);
        }
    }

    private void ensureCollection(MongoDatabase database, String name) {
        if (!plugin.getConfig().getBoolean("mongodb.auto-create-database", true)) {
            return;
        }
        for (String existing : database.listCollectionNames()) {
            if (existing.equals(name)) {
                return;
            }
        }
        database.createCollection(name, new CreateCollectionOptions());
    }

    private void ensureIndexes(MongoCollection<Document> collection) {
        collection.createIndex(Indexes.ascending("uuid"), new IndexOptions().unique(true).name("uniq_uuid"));
        collection.createIndex(Indexes.ascending("nameLower"), new IndexOptions().name("idx_name_lower"));
        collection.createIndex(Indexes.descending("wins"), new IndexOptions().name("idx_wins"));
        collection.createIndex(Indexes.descending("games"), new IndexOptions().name("idx_games"));
        collection.createIndex(Indexes.descending("itemsCollected"), new IndexOptions().name("idx_items_collected"));
        collection.createIndex(Indexes.descending("bestStreak"), new IndexOptions().name("idx_best_streak"));
    }

    @Override
    public void close() {
        closing = true;
        available = false;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } finally {
            closeClient();
        }
    }

    private void closeClient() {
        MongoClient current = client;
        client = null;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException ignored) {
                // Closing is best effort during server shutdown.
            }
        }
    }

    @Override
    public boolean available() {
        return available && !closing;
    }

    @Override
    public CompletableFuture<PlayerStats> getOrCreate(UUID uuid, String name) {
        PlayerStats memory = cached(uuid, name);
        if (!available()) {
            return CompletableFuture.completedFuture(memory);
        }
        return submit(() -> {
            String safeName = safeName(name);
            players.updateOne(Filters.eq("uuid", uuid.toString()), Updates.combine(
                    Updates.set("name", safeName), Updates.set("nameLower", safeName.toLowerCase(Locale.ROOT)),
                    Updates.setOnInsert("games", 0L), Updates.setOnInsert("wins", 0L),
                    Updates.setOnInsert("itemsCollected", 0L), Updates.setOnInsert("timeouts", 0L),
                    Updates.setOnInsert("streak", 0L), Updates.setOnInsert("bestStreak", 0L),
                    Updates.setOnInsert("createdAt", Instant.now().toEpochMilli())), new UpdateOptions().upsert(true));
            Document document = players.find(Filters.eq("uuid", uuid.toString())).first();
            PlayerStats result = fromDocument(document, uuid, safeName);
            cache.put(uuid, result);
            return result;
        }, memory);
    }

    @Override
    public PlayerStats cached(UUID uuid, String name) {
        return cache.compute(uuid, (key, old) -> {
            if (old == null) {
                return new PlayerStats(key, safeName(name));
            }
            if (name != null && !name.isBlank()) {
                old.name(name);
            }
            return old;
        });
    }

    @Override
    public CompletableFuture<Optional<PlayerStats>> findByName(String name) {
        if (name == null || name.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (!available()) {
            return CompletableFuture.completedFuture(cache.values().stream()
                    .filter(stats -> stats.name().equalsIgnoreCase(lower))
                    .findFirst());
        }
        return submit(() -> {
            Document document = players.find(Filters.eq("nameLower", lower)).first();
            if (document == null) {
                return Optional.empty();
            }
            UUID uuid = UUID.fromString(document.getString("uuid"));
            PlayerStats result = fromDocument(document, uuid, document.getString("name"));
            cache.put(uuid, result);
            return Optional.of(result);
        }, Optional.empty());
    }

    @Override
    public CompletableFuture<List<PlayerStats>> top(String field, int limit) {
        if (!NUMERIC_FIELDS.contains(field)) {
            return CompletableFuture.completedFuture(List.of());
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        if (!available()) {
            java.util.Comparator<PlayerStats> comparator = switch (field) {
                case "wins" -> java.util.Comparator.comparingLong(PlayerStats::wins);
                case "games" -> java.util.Comparator.comparingLong(PlayerStats::games);
                case "itemsCollected" -> java.util.Comparator.comparingLong(PlayerStats::itemsCollected);
                case "timeouts" -> java.util.Comparator.comparingLong(PlayerStats::timeouts);
                case "streak" -> java.util.Comparator.comparingLong(PlayerStats::streak);
                case "bestStreak" -> java.util.Comparator.comparingLong(PlayerStats::bestStreak);
                default -> java.util.Comparator.comparingLong(stats -> 0L);
            };
            return CompletableFuture.completedFuture(cache.values().stream()
                    .sorted(comparator.reversed())
                    .limit(safeLimit)
                    .toList());
        }
        return submit(() -> {
            List<PlayerStats> result = new ArrayList<>();
            FindIterable<Document> documents = players.find().sort(Sorts.descending(field)).limit(safeLimit);
            for (Document document : documents) {
                UUID uuid = UUID.fromString(document.getString("uuid"));
                PlayerStats stats = fromDocument(document, uuid, document.getString("name"));
                cache.put(uuid, stats);
                result.add(stats);
            }
            return result;
        }, List.of());
    }

    @Override
    public void recordGame(UUID uuid, String name, boolean winner, long itemsCollected, boolean timedOut) {
        if (!available()) {
            PlayerStats stats = cached(uuid, name);
            applyRecord(stats, winner, itemsCollected, timedOut);
            return;
        }
        executor.execute(() -> {
            if (!available()) {
                applyRecord(cached(uuid, name), winner, itemsCollected, timedOut);
                return;
            }
            try {
                Document current = players.find(Filters.eq("uuid", uuid.toString())).first();
                long streak = current == null ? 0L : number(current, "streak");
                long best = current == null ? 0L : number(current, "bestStreak");
                long nextStreak = winner ? streak + 1L : 0L;
                String safeName = safeName(name);
                long progress = Math.max(0L, itemsCollected);
                players.updateOne(Filters.eq("uuid", uuid.toString()), Updates.combine(
                        Updates.set("name", safeName), Updates.set("nameLower", safeName.toLowerCase(Locale.ROOT)),
                        Updates.inc("games", 1L), Updates.inc("wins", winner ? 1L : 0L),
                        Updates.inc("itemsCollected", progress), Updates.inc("timeouts", timedOut ? 1L : 0L),
                        Updates.set("streak", nextStreak), Updates.set("bestStreak", Math.max(best, nextStreak)),
                        Updates.set("lastPlayedAt", Instant.now().toEpochMilli())), new UpdateOptions().upsert(true));
                Document updated = players.find(Filters.eq("uuid", uuid.toString())).first();
                cache.put(uuid, fromDocument(updated, uuid, safeName));
                if (games != null) {
                    games.insertOne(new Document("uuid", uuid.toString()).append("name", safeName)
                            .append("winner", winner).append("itemsCollected", progress).append("timedOut", timedOut)
                            .append("playedAt", Instant.now().toEpochMilli()));
                }
            } catch (Exception exception) {
                PlayerStats stats = cached(uuid, name);
                applyRecord(stats, winner, itemsCollected, timedOut);
                markUnavailable(exception);
            }
        });
    }

    private void applyRecord(PlayerStats stats, boolean winner, long itemsCollected, boolean timedOut) {
        stats.games(stats.games() + 1L);
        stats.wins(stats.wins() + (winner ? 1L : 0L));
        stats.itemsCollected(stats.itemsCollected() + Math.max(0L, itemsCollected));
        stats.timeouts(stats.timeouts() + (timedOut ? 1L : 0L));
        long streak = winner ? stats.streak() + 1L : 0L;
        stats.streak(streak);
        stats.bestStreak(Math.max(stats.bestStreak(), streak));
    }

    private <T> CompletableFuture<T> submit(java.util.concurrent.Callable<T> operation, T fallback) {
        CompletableFuture<T> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                future.complete(operation.call());
            } catch (Exception exception) {
                future.complete(fallback);
                markUnavailable(exception);
            }
        });
        return future;
    }

    private PlayerStats fromDocument(Document document, UUID uuid, String fallbackName) {
        PlayerStats stats = new PlayerStats(uuid, safeName(fallbackName));
        if (document == null) {
            return stats;
        }
        stats.name(document.getString("name"));
        stats.games(number(document, "games"));
        stats.wins(number(document, "wins"));
        stats.itemsCollected(number(document, "itemsCollected"));
        stats.timeouts(number(document, "timeouts"));
        stats.streak(number(document, "streak"));
        stats.bestStreak(number(document, "bestStreak"));
        return stats;
    }

    private long number(Document document, String key) {
        Object value = document.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String safeName(String name) {
        return name == null || name.isBlank() ? "unknown" : name;
    }

    private void markUnavailable(Exception exception) {
        available = false;
        closeClient();
        reportFailure("MongoDB statistics failed; switched to memory-only mode", exception);
    }

    private void reportFailure(String message, Exception exception) {
        if (failureReported.compareAndSet(false, true)) {
            plugin.getLogger().log(Level.WARNING, message + ": " + exception.getMessage());
        }
    }
}
