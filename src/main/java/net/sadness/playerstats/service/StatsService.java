package net.sadness.playerstats.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sadness.playerstats.stats.LeaderboardEntry;
import net.sadness.playerstats.stats.LeaderboardPage;
import net.sadness.playerstats.stats.Pagination;
import net.sadness.playerstats.stats.PlayerDelta;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatField;
import net.sadness.playerstats.stats.StatValues;
import net.sadness.playerstats.storage.StatsRepository;
import net.sadness.playerstats.storage.StorageException;
import net.sadness.playerstats.storage.StorageExecutor;
import net.sadness.playerstats.storage.StorageTask;

public final class StatsService {

    private final StatsRepository repository;
    private final StorageExecutor executor;
    private final LongSupplier clock;
    private final Logger logger;
    private final Map<UUID, PlayerSession> online = new ConcurrentHashMap<>();
    private final Set<PlayerSession> departed = ConcurrentHashMap.newKeySet();

    public StatsService(StatsRepository repository, StorageExecutor executor, LongSupplier clock, Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void track(UUID id, String name, boolean countJoin) {
        PlayerSession session = new PlayerSession(id, name, clock.getAsLong());
        if (countJoin) {
            session.add(StatField.JOINS, 1);
        }
        PlayerSession previous = online.put(id, session);
        if (previous != null) {
            retire(previous);
        }
        submitSafely(() -> repository.findById(id)).whenComplete((record, failure) -> {
            if (failure == null) {
                session.completeLoad(record.map(PlayerRecord::values).orElse(StatValues.ZERO));
            } else {
                logger.log(Level.WARNING, "Could not load statistics for " + name + "; reads will fall back to the database", failure);
            }
        });
    }

    public void untrack(UUID id) {
        PlayerSession session = online.remove(id);
        if (session != null) {
            retire(session);
        }
    }

    public void record(UUID id, StatField field, long amount) {
        if (amount <= 0) {
            return;
        }
        PlayerSession session = online.get(id);
        if (session != null) {
            session.add(field, amount);
        }
    }

    public CompletableFuture<Void> flushAll() {
        long now = clock.getAsLong();
        List<PlayerSession> sessions = new ArrayList<>(online.values());
        sessions.forEach(session -> session.accrue(now));
        sessions.addAll(departed);
        return flush(sessions);
    }

    public CompletableFuture<Optional<PlayerRecord>> find(UUID id) {
        PlayerSession session = online.get(id);
        if (session == null) {
            return submitSafely(() -> repository.findById(id));
        }
        session.accrue(clock.getAsLong());
        Optional<StatValues> view = session.view();
        if (view.isPresent()) {
            return CompletableFuture.completedFuture(Optional.of(new PlayerRecord(id, session.name(), view.get())));
        }
        return flush(List.of(session)).thenCompose(ignored -> submitSafely(() -> repository.findById(id)));
    }

    public CompletableFuture<Optional<PlayerRecord>> findByName(String name) {
        for (PlayerSession session : online.values()) {
            if (session.name().equalsIgnoreCase(name)) {
                return find(session.id());
            }
        }
        return submitSafely(() -> repository.findByName(name));
    }

    public CompletableFuture<LeaderboardPage> top(StatField field, int requestedPage, int pageSize) {
        return flushAll().thenCompose(ignored -> submitSafely(() -> {
            long total = repository.countRanked(field);
            Pagination pagination = Pagination.of(total, pageSize, requestedPage);
            List<LeaderboardEntry> entries = total == 0 ? List.of() : repository.top(field, pagination.pageSize(), pagination.offset());
            return new LeaderboardPage(field, pagination, entries);
        }));
    }

    public CompletableFuture<Void> set(UUID id, String name, StatField field, long displayValue) {
        try {
            long stored = field.toStored(displayValue);
            StorageTask<Void> write = () -> {
                repository.set(id, name, field, stored);
                return null;
            };
            PlayerSession session = online.get(id);
            if (session == null) {
                return executor.submit(write);
            }
            synchronized (session) {
                if (!session.isLoaded()) {
                    throw new StatsNotLoadedException();
                }
                CompletableFuture<Void> result = executor.submit(write);
                session.applySet(field, stored);
                return result;
            }
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public CompletableFuture<Void> reset(UUID id) {
        try {
            StorageTask<Void> write = () -> {
                repository.reset(id);
                return null;
            };
            PlayerSession session = online.get(id);
            if (session == null) {
                return executor.submit(write);
            }
            synchronized (session) {
                if (!session.isLoaded()) {
                    throw new StatsNotLoadedException();
                }
                CompletableFuture<Void> result = executor.submit(write);
                session.applyReset();
                return result;
            }
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public void shutdown(Duration timeout) {
        flushAll();
        if (!executor.shutdownAndAwait(timeout)) {
            logger.severe("Storage queue did not drain within " + timeout.toSeconds() + "s; "
                    + executor.abort() + " queued operations were discarded");
        }
        long now = clock.getAsLong();
        List<PlayerSession> sessions = new ArrayList<>(online.values());
        sessions.addAll(departed);
        sessions.forEach(session -> session.close(now));
        List<PendingFlush> pending = drain(sessions);
        if (!pending.isEmpty()) {
            try {
                write(pending);
                pending.forEach(PendingFlush::acknowledge);
            } catch (StorageException failure) {
                pending.forEach(PendingFlush::restore);
                logger.log(Level.SEVERE, "Final statistics flush failed; data for " + pending.size() + " players was lost", failure);
            }
        }
        online.clear();
        departed.clear();
    }

    private void retire(PlayerSession session) {
        session.close(clock.getAsLong());
        departed.add(session);
        flush(List.of(session));
    }

    private CompletableFuture<Void> flush(Collection<PlayerSession> sessions) {
        List<PendingFlush> pending = drain(sessions);
        if (pending.isEmpty()) {
            departed.removeIf(PlayerSession::isSettled);
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> submitted;
        try {
            submitted = executor.submit(() -> {
                write(pending);
                return null;
            });
        } catch (RejectedExecutionException rejected) {
            pending.forEach(PendingFlush::restore);
            logger.warning("Storage queue is full; statistics will be retried on the next flush");
            return CompletableFuture.failedFuture(rejected);
        }
        return submitted.whenComplete((ignored, failure) -> {
            if (failure == null) {
                pending.forEach(PendingFlush::acknowledge);
            } else {
                pending.forEach(PendingFlush::restore);
                logger.log(Level.WARNING, "Statistics flush failed; it will be retried on the next flush", failure);
            }
            departed.removeIf(PlayerSession::isSettled);
        });
    }

    private List<PendingFlush> drain(Collection<PlayerSession> sessions) {
        List<PendingFlush> pending = new ArrayList<>();
        for (PlayerSession session : sessions) {
            session.drain().ifPresent(drain -> pending.add(new PendingFlush(session, drain)));
        }
        return pending;
    }

    private void write(List<PendingFlush> pending) throws StorageException {
        List<PlayerDelta> deltas = new ArrayList<>(pending.size());
        for (PendingFlush flush : pending) {
            StatValues values = flush.session().currentDelta(flush.drain());
            if (!values.isZero()) {
                deltas.add(new PlayerDelta(flush.session().id(), flush.session().name(), values));
            }
        }
        repository.applyDeltas(deltas);
    }

    private <T> CompletableFuture<T> submitSafely(StorageTask<T> task) {
        try {
            return executor.submit(task);
        } catch (RejectedExecutionException rejected) {
            return CompletableFuture.failedFuture(rejected);
        }
    }

    private record PendingFlush(PlayerSession session, PlayerSession.Drain drain) {

        void acknowledge() {
            session.acknowledge();
        }

        void restore() {
            session.restore(drain);
        }
    }
}
