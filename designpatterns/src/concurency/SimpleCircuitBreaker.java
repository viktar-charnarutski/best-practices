package concurency;

import java.util.concurrent.atomic.AtomicReference;

/**
 * <em>Circuit breaker</em> is a mechanism which allows to avoid unnecessary calls to a resource
 * (e.g. third-party service) which is currently experiencing difficulties with requests' processing.
 * <p>
 * With the help of this <em>circuit breaker</em> all calls to the resource are being monitored, and as soon as
 * defined <em>threshold</em> of failed requests is reached, the <em>circuit breaker</em> switches to <em>open</em>
 * state, which means all further requests to the resource are prohibited. This outage is in progress until the
 * specified <em>timeout</em> is not passed since the outage was initiated. When the specified <em>timeout</em> is
 * passed, the <em>circuit breaker</em> switches back to <em>closed</em> state.
 * <p>
 * This <em>circuit breaker</em> could help to avoid performance degradation caused by timeouts received for
 * synchronous calls to a third-party service.
 * <p>
 * Usage example with allowed 5 failed calls (<em>threshold</em>), after the <em>threshold</em> is reached,
 * an alternative processing will be handling for a 1 minute (<em>timeout</em>):
 * <pre>
 *     {@code
 *     SimpleCircuitBreaker serviceWatcher = new SimpleCircuitBreaker(5, 60000L);
 *     if (!serviceWatcher.isOutageInProgress()) {
 *       try {
 *             // call to a third-party service
 *           } catch (RuntimeException e) {
 *                serviceWatcher.requestOutage();
 *                // process alternatives
 *           }
 *       } else {
 *          // process alternatives
 *     }
 *     }
 * </pre>
 *
 * @author Viktar Charnarutski
 */
public class SimpleCircuitBreaker {

    private final int failureAttemptsThreshold;
    private final long outageTimeout;

    private final AtomicReference<OutageState> state = new AtomicReference<>(OutageState.CLOSED);
    private final AtomicReference<Failure> currentOutage = new AtomicReference<>(new Failure(0, 0));

    /**
     * Creates a new instance on the {@code SimpleCircuitBreaker} and initialize threshold and timeout values
     * required to operate the <em>circuit breaker</em> states.
     *
     * @param failureAttemptsThreshold amount of failed calls which automatically switches the <em>circuit breaker</em>
     *                                 to <em>open</em> state.
     * @param outageTimeout            timeout which define a longevity of outage. When the specified <em>timeout</em> is
     *                                 passed, the <em>circuit breaker</em> switches back to <em>closed</em> state.
     */
    public SimpleCircuitBreaker(int failureAttemptsThreshold, long outageTimeout) {
        this.failureAttemptsThreshold = failureAttemptsThreshold;
        this.outageTimeout = outageTimeout;
    }

    /**
     * Returns a specified amount of failed calls which automatically switches the <em>circuit breaker</em>
     * to <em>open</em> state.
     *
     * @return failed calls threshold
     */
    public int failureAttemptsThreshold() {
        return failureAttemptsThreshold;
    }

    /**
     * Returns a specified timeout which define a longevity of outage. When the specified <em>timeout</em> is
     * passed, the <em>circuit breaker</em> switches back to <em>closed</em> state.
     *
     * @return outage timeout
     */
    public long outageTimeout() {
        return outageTimeout;
    }

    /**
     * Checks if the <em>circuit breaker</em> is in <em>open</em> state.
     * <p>
     * If the <em>circuit breaker</em> is in <em>open</em> state and the specified outage timeout is already passed,
     * it requests switching back to <em>closed</em> state.
     *
     * @return {@code true} if the <em>circuit breaker</em> is in <em>open</em> state, {@code false} if not.
     */
    public boolean isOutageInProgress() {
        if (isOutageOpened() && isTimeoutExpired()) {
            closeOutage();
        }
        return isOutageOpened();
    }

    /**
     * Requests the <em>circuit breaker</em> to be switched to <em>open</em> state if it's currently in <em>closed</em>
     * state and the defined <em>failed calls threshold</em> is reached.
     */
    public void requestOutage() {
        Failure failure = currentOutage.get().increment();
        currentOutage.set(failure);
        if (isOutageClosed() && isThresholdReached()) {
            openOutage();
        }
    }

    /**
     * Sets the <em>circuit breaker</em> to <em>open</em> state preserving a failed attempts number and setting the
     * current time (in milliseconds) for outage's start time.
     * <p>
     * The operation is allowed only if the current state is <em>closed</em>, otherwise the call is ignored.
     * <p>
     * The method is marked as {@code synchronized} to prevent simultaneous outage opening attempts.
     */
    private synchronized void openOutage() {
        if (isOutageClosed()) {
            state.compareAndSet(OutageState.CLOSED, OutageState.OPEN);
            int failureAttempts = currentOutage.get().failureAttempts();
            currentOutage.set(new Failure(failureAttempts, System.currentTimeMillis()));
        }
    }

    /**
     * Sets the <em>circuit breaker</em> to <em>closed</em> state resetting a failed attempts number
     * and outage's start time.
     * <p>
     * The operation is allowed only if the current state is <em>open</em>, otherwise the call is ignored.
     * <p>
     * The method is marked as {@code synchronized} to prevent simultaneous outage closing attempts.
     */
    private synchronized void closeOutage() {
        if (isOutageOpened()) {
            state.compareAndSet(OutageState.OPEN, OutageState.CLOSED);
            currentOutage.set(new Failure(0, 0));
        }
    }

    /**
     * Checks whether the <em>circuit breaker</em> is in <em>open</em> state.
     *
     * @return {@code true} if the <em>circuit breaker</em> is in <em>open</em> state, {@code false} if not.
     */
    private boolean isOutageOpened() {
        return state.get() == OutageState.OPEN;
    }

    /**
     * Checks whether the <em>circuit breaker</em> is in <em>closed</em> state.
     *
     * @return {@code true} if the <em>circuit breaker</em> is in <em>closed</em> state, {@code false} if not.
     */
    private boolean isOutageClosed() {
        return state.get() == OutageState.CLOSED;
    }

    /**
     * Checks whether the current failure attempts number reached the defined threshold.
     *
     * @return {@code true} if the current failure attempts number reached the defined threshold, {@code false} if not.
     */
    private boolean isThresholdReached() {
        return currentOutage.get().failureAttempts() >= failureAttemptsThreshold;
    }

    /**
     * Checks whether the current outage's timeout is expired.
     *
     * @return {@code true} if the current outage's timeout is expired, {@code false} if not.
     */
    private boolean isTimeoutExpired() {
        return System.currentTimeMillis() - currentOutage.get().startTime() > outageTimeout;
    }

    /**
     * Internal class for the current outage's information holding.
     */
    private static class Failure {
        private final int failureAttempts;
        private final long startTime;

        /**
         * Creates an immutable instance of {@code Failure} initializing it with the current failure attempts and
         * a time when the <em>circuit breaker</em> was switched to <em>open</em> state.
         *
         * @param failureAttempts current failure attempts
         * @param startTime       time when the <em>circuit breaker</em> was switched to <em>open</em> state
         */
        public Failure(int failureAttempts, long startTime) {
            this.failureAttempts = failureAttempts;
            this.startTime = startTime;
        }

        /**
         * Returns a current number of failure attempts.
         *
         * @return current number of failure attempts
         */
        public int failureAttempts() {
            return failureAttempts;
        }

        /**
         * Returns a time in milliseconds when the <em>circuit breaker</em> was switched to <em>open</em> state.
         *
         * @return time in milliseconds when the <em>circuit breaker</em> was switched to <em>open</em> state
         */
        public long startTime() {
            return startTime;
        }

        /**
         * Increments by one the current number of failure attempts and returns a new {@code Failure} object initialized
         * with incremented number of failure attempts and the time when the <em>circuit breaker</em> was switched
         * to <em>open</em> state.
         *
         * @return new {@code Failure} object initialized with incremented number of failure attempts and the time when
         * the <em>circuit breaker</em> was switched to <em>open</em> state
         */
        public Failure increment() {
            return new Failure(failureAttempts + 1, startTime);
        }
    }

    /**
     * Internal enum for <em>circuit breaker</em>'s states representation.
     */
    private enum OutageState {
        OPEN, CLOSED
    }
}

