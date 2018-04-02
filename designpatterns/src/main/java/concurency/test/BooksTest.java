package concurency.test;

import model.Books;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testing Java Classes for Thread-Safety by Yegor Bugayenko.
 *
 * http://www.yegor256.com/2018/03/27/how-to-test-thread-safety.html
 */
public class BooksTest {
    private int threads = 10;
    private Books books = new Books();

    @Test
    public void addsAndRetrieves() throws ExecutionException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean();
        AtomicInteger overlaps = new AtomicInteger();

        Collection<Future<Integer>> futures = new ArrayList<>(threads);
        ExecutorService service = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; ++t) {
            final String title = String.format("Book #%d", t);
            futures.add(
                    service.submit(
                            () -> {
                                latch.await();
                                if (running.get()) {
                                    overlaps.incrementAndGet();
                                }
                                running.set(true);
                                int id = books.add(title);
                                running.set(false);
                                return id;
                            }
                    )
            );
        }
        latch.countDown();
        Set<Integer> ids = new HashSet<>();
        for (Future<Integer> f : futures) {
            ids.add(f.get());
        }

        assertTrue(overlaps.get() > 0);
    }
}