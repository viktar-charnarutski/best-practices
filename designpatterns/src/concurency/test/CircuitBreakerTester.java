package concurency.test;

import concurency.SimpleCircuitBreaker;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

/**
 * A simple client created for {@code SimpleCircuitBreaker} testing purpose.
 */
public class CircuitBreakerTester {
    private SimpleCircuitBreaker watcher = new SimpleCircuitBreaker(5, 10000L);

    private void externalCall() {
        if (!watcher.isOutageInProgress()) {
            try {
                if (randomNum() % 10 == 0) {
                    throw new RuntimeException("Failed to call the service");
                }
                System.out.println(new Date(System.currentTimeMillis()) + ": Successful call to external service");
            } catch (RuntimeException e) {
                System.out.println(new Date(System.currentTimeMillis()) + ": " + e.getMessage() + ". Requesting outage...");
                watcher.requestOutage();
            }
        } else {
            System.out.println(new Date(System.currentTimeMillis()) + ": Outage is in progress...");
        }
    }

    private static int randomNum() {
        return new Random().nextInt(500) + 1;
    }

    public static void main(String[] args) {
        final CircuitBreakerTester t = new CircuitBreakerTester();
        final CyclicBarrier gate = new CyclicBarrier(4);
        final Runnable runnable = () -> {
            try {
                gate.await();
                for (int i = 0; i < 100; i++) {
                    t.externalCall();
                    Thread.sleep(1000);
                }
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        };
        Thread tr1 = new Thread(runnable);
        Thread tr2 = new Thread(runnable);
        Thread tr3 = new Thread(runnable);

        tr1.start();
        tr2.start();
        tr3.start();

        try {
            System.out.println(new Date(System.currentTimeMillis()) + ": Starting all threads...");
            gate.await();
        } catch (InterruptedException | BrokenBarrierException e) {
            e.printStackTrace();
        }

    }
}

