package net.sadness.playerstats.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sadness.playerstats.stats.LeaderboardPage;
import net.sadness.playerstats.stats.PlayerRecord;
import net.sadness.playerstats.stats.StatField;
import net.sadness.playerstats.stats.StatValues;
import net.sadness.playerstats.storage.StorageException;
import net.sadness.playerstats.storage.StorageExecutor;
import net.sadness.playerstats.storage.StorageTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatsServiceTest {

    private static final long TIMEOUT_SECONDS = 10;
    private static final Duration SHUTDOWN = Duration.ofSeconds(5);

    private final UUID id = UUID.randomUUID();
    private final AtomicLong clock = new AtomicLong(1_000_000);
    private InMemoryRepository repository;
    private StorageExecutor executor;
    private StatsService service;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger("StatsServiceTest");
        logger.setLevel(Level.OFF);
        repository = new InMemoryRepository();
        executor = new StorageExecutor(64);
        service = new StatsService(repository, executor, clock::get, logger);
    }

    @AfterEach
    void tearDown() {
        executor.abort();
    }

    private PlayerRecord stored() {
        return repository.findById(id).orElseThrow();
    }

    private void await(CompletableFuture<?> future) throws Exception {
        future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static StorageTask<Void> blockUntil(CountDownLatch release) {
        return () -> {
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return null;
        };
    }

    private void waitForLoad() throws Exception {
        await(executor.submit(() -> null));
    }

    @Test
    void joinCountsAndFlushPersists() throws Exception {
        service.track(id, "Steve", true);
        await(service.flushAll());
        assertEquals(1, stored().values().get(StatField.JOINS));
    }

    @Test
    void reconnectWithoutJoinCountDoesNotIncrementJoins() throws Exception {
        service.track(id, "Steve", false);
        service.record(id, StatField.DEATHS, 1);
        await(service.flushAll());
        assertEquals(0, stored().values().get(StatField.JOINS));
        assertEquals(1, stored().values().get(StatField.DEATHS));
    }

    @Test
    void eventsBeforeLoadCompletesAreNotLost() throws Exception {
        repository.seed(id, "Steve", StatValues.of(StatField.DEATHS, 10));
        service.track(id, "Steve", true);
        service.record(id, StatField.DEATHS, 2);
        await(service.flushAll());
        assertEquals(12, stored().values().get(StatField.DEATHS));
        assertEquals(1, stored().values().get(StatField.JOINS));
    }

    @Test
    void repeatedFlushesDoNotDoubleCount() throws Exception {
        service.track(id, "Steve", true);
        service.record(id, StatField.BLOCKS_BROKEN, 5);
        await(service.flushAll());
        await(service.flushAll());
        service.record(id, StatField.BLOCKS_BROKEN, 3);
        await(service.flushAll());
        assertEquals(8, stored().values().get(StatField.BLOCKS_BROKEN));
    }

    @Test
    void emptyFlushDoesNotTouchStorage() throws Exception {
        service.track(id, "Steve", false);
        await(service.flushAll());
        assertEquals(0, repository.writeCalls.get());
    }

    @Test
    void nonPositiveAmountsAndUnknownPlayersAreIgnored() throws Exception {
        service.track(id, "Steve", false);
        service.record(id, StatField.DEATHS, 0);
        service.record(id, StatField.DEATHS, -4);
        service.record(UUID.randomUUID(), StatField.DEATHS, 1);
        await(service.flushAll());
        assertEquals(0, repository.writeCalls.get());
    }

    @Test
    void playtimeAccruesInWholeSecondsWithoutLosingRemainder() throws Exception {
        service.track(id, "Steve", false);
        clock.addAndGet(1_500);
        await(service.flushAll());
        assertEquals(1, stored().values().get(StatField.PLAYTIME));
        clock.addAndGet(600);
        await(service.flushAll());
        assertEquals(2, stored().values().get(StatField.PLAYTIME));
    }

    @Test
    void untrackFlushesPlaytimeAndFurtherEventsAreIgnored() throws Exception {
        service.track(id, "Steve", true);
        clock.addAndGet(30_000);
        service.untrack(id);
        service.record(id, StatField.DEATHS, 4);
        clock.addAndGet(30_000);
        service.shutdown(SHUTDOWN);
        assertEquals(30, stored().values().get(StatField.PLAYTIME));
        assertEquals(1, stored().values().get(StatField.JOINS));
        assertEquals(0, stored().values().get(StatField.DEATHS));
    }

    @Test
    void failedFlushIsRetriedWithoutLoss() throws Exception {
        service.track(id, "Steve", true);
        service.record(id, StatField.DEATHS, 3);
        repository.failWrites.set(true);
        ExecutionException failure = assertThrows(ExecutionException.class, () -> service.flushAll().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(StorageException.class, failure.getCause());
        repository.failWrites.set(false);
        await(service.flushAll());
        assertEquals(3, stored().values().get(StatField.DEATHS));
        assertEquals(1, stored().values().get(StatField.JOINS));
    }

    @Test
    void shutdownPerformsSynchronousFinalFlush() {
        service.track(id, "Steve", true);
        service.record(id, StatField.MOB_KILLS, 7);
        clock.addAndGet(5_000);
        service.shutdown(SHUTDOWN);
        assertEquals(7, stored().values().get(StatField.MOB_KILLS));
        assertEquals(5, stored().values().get(StatField.PLAYTIME));
    }

    @Test
    void shutdownRetriesAfterFailedAsyncFlush() throws Exception {
        service.track(id, "Steve", true);
        service.record(id, StatField.DEATHS, 2);
        repository.failWrites.set(true);
        service.flushAll().handle((ok, failure) -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        repository.failWrites.set(false);
        service.shutdown(SHUTDOWN);
        assertEquals(2, stored().values().get(StatField.DEATHS));
    }

    @Test
    void findReflectsUnflushedActivityOnTopOfStoredValues() throws Exception {
        repository.seed(id, "Steve", StatValues.of(StatField.DEATHS, 10));
        service.track(id, "Steve", false);
        waitForLoad();
        service.record(id, StatField.DEATHS, 5);
        PlayerRecord view = service.find(id).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).orElseThrow();
        assertEquals(15, view.values().get(StatField.DEATHS));
    }

    @Test
    void findByNameMatchesOnlinePlayersCaseInsensitively() throws Exception {
        service.track(id, "Steve", true);
        PlayerRecord view = service.findByName("sTeVe").get(TIMEOUT_SECONDS, TimeUnit.SECONDS).orElseThrow();
        assertEquals(id, view.id());
        assertEquals(1, view.values().get(StatField.JOINS));
    }

    @Test
    void findOfflinePlayerReadsStorage() throws Exception {
        UUID other = UUID.randomUUID();
        repository.seed(other, "Alex", StatValues.of(StatField.JOINS, 4));
        assertEquals(4, service.find(other).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).orElseThrow().values().get(StatField.JOINS));
        assertTrue(service.findByName("ghost").get(TIMEOUT_SECONDS, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    void topFlushesPendingDataFirstAndPaginates() throws Exception {
        service.track(id, "Steve", true);
        service.record(id, StatField.DEATHS, 9);
        repository.seed(UUID.randomUUID(), "Alex", StatValues.of(StatField.DEATHS, 3));
        LeaderboardPage page = service.top(StatField.DEATHS, 1, 1).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(2, page.pagination().totalPages());
        assertEquals("Steve", page.entries().get(0).name());
        LeaderboardPage second = service.top(StatField.DEATHS, 99, 1).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals(2, second.pagination().page());
        assertEquals("Alex", second.entries().get(0).name());
    }

    @Test
    void topOfEmptyBoardIsEmpty() throws Exception {
        LeaderboardPage page = service.top(StatField.MOB_KILLS, 1, 10).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(page.entries().isEmpty());
        assertEquals(1, page.pagination().totalPages());
    }

    @Test
    void setReplacesStoredValueAndDiscardsPendingForThatField() throws Exception {
        repository.seed(id, "Steve", StatValues.of(StatField.DEATHS, 10));
        service.track(id, "Steve", false);
        waitForLoad();
        service.record(id, StatField.DEATHS, 5);
        service.record(id, StatField.MOB_KILLS, 2);
        await(service.set(id, "Steve", StatField.DEATHS, 1));
        await(service.flushAll());
        assertEquals(1, stored().values().get(StatField.DEATHS));
        assertEquals(2, stored().values().get(StatField.MOB_KILLS));
        assertEquals(1, service.find(id).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).orElseThrow().values().get(StatField.DEATHS));
    }

    @Test
    void setConvertsDisplayUnitsAndRejectsOutOfRange() throws Exception {
        UUID other = UUID.randomUUID();
        await(service.set(other, "Alex", StatField.DISTANCE_WALKED, 3));
        assertEquals(300, repository.findById(other).orElseThrow().values().get(StatField.DISTANCE_WALKED));
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> service.set(other, "Alex", StatField.DEATHS, -1).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    @Test
    void resetClearsStoredAndPendingValues() throws Exception {
        repository.seed(id, "Steve", StatValues.of(StatField.DEATHS, 10));
        service.track(id, "Steve", false);
        waitForLoad();
        service.record(id, StatField.DEATHS, 5);
        await(service.reset(id));
        await(service.flushAll());
        assertTrue(stored().values().isZero());
        service.record(id, StatField.DEATHS, 1);
        await(service.flushAll());
        assertEquals(1, stored().values().get(StatField.DEATHS));
    }

    @Test
    void setBeforeLoadIsRejected() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
executor.submit(blockUntil(release));
        service.track(id, "Steve", false);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> service.set(id, "Steve", StatField.DEATHS, 1).get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(StatsNotLoadedException.class, failure.getCause());
        release.countDown();
    }

    @Test
    void fullQueueRejectsWithoutLosingData() throws Exception {
        StorageExecutor tiny = new StorageExecutor(1);
        StatsService small = new StatsService(repository, tiny, clock::get, Logger.getAnonymousLogger());
        CountDownLatch release = new CountDownLatch(1);
tiny.submit(blockUntil(release));
        tiny.submit(() -> null);
        small.track(id, "Steve", false);
        small.record(id, StatField.DEATHS, 4);
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> small.flushAll().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class, failure.getCause());
        release.countDown();
        small.shutdown(SHUTDOWN);
        assertEquals(4, stored().values().get(StatField.DEATHS));
    }

    @Test
    void concurrentRecordingAndFlushingLosesNothing() throws Exception {
        service.track(id, "Steve", false);
        int threads = 6;
        int perThread = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads + 1);
        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<?>[] workers = new CompletableFuture<?>[threads];
        for (int t = 0; t < threads; t++) {
            workers[t] = CompletableFuture.runAsync(() -> {
                try {
                    start.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perThread; i++) {
                    service.record(id, StatField.BLOCKS_PLACED, 1);
                }
            }, pool);
        }
        CompletableFuture<Void> flusher = CompletableFuture.runAsync(() -> {
            try {
                start.await();
                for (int i = 0; i < 20; i++) {
                    service.flushAll().handle((ok, failure) -> null).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }
            } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException failure) {
                throw new IllegalStateException(failure);
            }
        }, pool);
        start.countDown();
        CompletableFuture.allOf(workers).get(30, TimeUnit.SECONDS);
        flusher.get(30, TimeUnit.SECONDS);
        pool.shutdownNow();
        service.shutdown(SHUTDOWN);
        assertEquals((long) threads * perThread, stored().values().get(StatField.BLOCKS_PLACED));
    }

    @Test
    void executorRejectsInvalidCapacityAndDrainsOnShutdown() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new StorageExecutor(0));
        StorageExecutor local = new StorageExecutor(4);
        AtomicLong ran = new AtomicLong();
        for (int i = 0; i < 4; i++) {
            local.submit(() -> ran.incrementAndGet());
        }
        assertTrue(local.shutdownAndAwait(SHUTDOWN));
        assertEquals(4, ran.get());
    }
}
