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
 * All the monitored failure statistics is relevant to a specified time frame. That means that the
 * <em>circuit breaker</em> will fire the outage flag only if the critical amount of failed calls were occurred
 * in a particular period of time. E.g. 5 calls failed with a timeout exception during a one minute.
 * <p>
 * This <em>circuit breaker</em> could help to avoid performance degradation caused by timeouts received for
 * synchronous calls to a third-party service.
 * <p>
 * Usage example with allowed 5 failed calls <em>threshold</em> within 1 minute time frame (60 000 milliseconds),
 * after the <em>threshold</em> is reached, an alternative processing will be handling for a 5 minutes
 * (300 000 milliseconds) <em>timeout</em>:
 * <pre>
 *     {@code
 *     SimpleCircuitBreaker serviceWatcher = new SimpleCircuitBreaker(5, 300000L, 60000L);
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
public class SimpleCircuitBreaker implements Outage {

    /**
     * Holds the specified threshold of failed calls.
     */
    private final int failureAttemptsThreshold;

    /**
     * Holds the specified outage's timeout.
     */
    private final long outageTimeout;

    /**
     * Holds the specified time frame for capturing failed attempts for specified threshold.
     */
    private final long monitoredTimeFrame;

    /**
     * Holds the current <em>circuit breaker</em>'s state.
     */
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    /**
     * Hold's the current outage's information.
     */
    private final AtomicReference<Failure> currentFailure = new AtomicReference<>(new Failure());

    /**
     * Creates a new instance on the {@code SimpleCircuitBreaker} and initialize threshold, timeout and
     * monitored time frame values required to operate the <em>circuit breaker</em> states.
     *
     * @param failureAttemptsThreshold amount of failed calls which automatically switches the <em>circuit breaker</em>
     *                                 to <em>open</em> state
     * @param outageTimeout            timeout which define a longevity of an outage
     * @param monitoredTimeFrame       time frame for capturing failed attempts for the specified threshold
     */
    public SimpleCircuitBreaker(int failureAttemptsThreshold, long outageTimeout, long monitoredTimeFrame) {
        this.failureAttemptsThreshold = failureAttemptsThreshold;
        this.outageTimeout = outageTimeout;
        this.monitoredTimeFrame = monitoredTimeFrame;
    }

    /**
     * Checks if the <em>outage</em> is in progress.
     * <p>
     * If the <em>outage</em> is in progress and the specified outage timeout is already passed,
     * it will finish the current outage.
     *
     * @return {@code true} if the <em>outage</em> is <em>in progress</em> state, {@code false} if not.
     */
    public boolean isOutageInProgress() {
        // check if the outage is in progress but could be closed
        // due to it's allowed longevity is expired
        if (isCircuitBreakerOpen() && isOpenStateTimeoutExpired()) {
            closeOutage();
        }
        return isCircuitBreakerOpen();
    }

    /**
     * Requests the <em>circuit breaker</em> to be switched to <em>open</em> state if it's currently in <em>closed</em>
     * state and the defined <em>failed calls threshold</em> is reached.
     * <p>
     * If the current monitored time frame is expired, the failure information will be reset.
     */
    public void requestOutage() {
        // check if the previous failure attempts were captured long time ago
        // and not actual any more
        if (isMonitoredTimeFrameExpired()) {
            Failure currentFailure = this.currentFailure.get();
            Failure newFailure = new Failure(1, System.currentTimeMillis(), 0L);
            this.currentFailure.compareAndSet(currentFailure, newFailure);
        } else {
            // increment failure attempts and then check
            // if it's already reached to a critical value
            Failure currentFailure = this.currentFailure.get();
            Failure newFailure = this.currentFailure.get().increment();
            this.currentFailure.compareAndSet(currentFailure, newFailure);
            if (isThresholdReached()) {
                openOutage();
            }
        }
    }

    /**
     * Sets the <em>circuit breaker</em> to <em>open</em> state preserving a failed attempts number and monitoring start
     * time. For outage's start time the current time is set (in milliseconds).
     * <p>
     * The operation is allowed only if the current state is <em>closed</em>, otherwise the call is ignored.
     */
    public void openOutage() {
        if (isCircuitBreakerClosed()) {
            state.compareAndSet(State.CLOSED, State.OPEN);
            Failure currentFailure = this.currentFailure.get();
            Failure newFailure = new Failure(currentFailure.failureAttempts(), currentFailure.monitoringStartTime(),
                    System.currentTimeMillis());
            this.currentFailure.compareAndSet(currentFailure, newFailure);
        }
    }

    /**
     * Sets the <em>circuit breaker</em> to <em>closed</em> state resetting a failed attempts number, monitoring
     * time frame and outage's start time.
     * <p>
     * The operation is allowed only if the current state is <em>open</em>, otherwise the call is ignored.
     */
    public void closeOutage() {
        if (isCircuitBreakerOpen()) {
            state.compareAndSet(State.OPEN, State.CLOSED);
            Failure currentFailure = this.currentFailure.get();
            Failure newFailure = new Failure();
            this.currentFailure.compareAndSet(currentFailure, newFailure);
        }
    }

    /**
     * Checks if the <em>circuit breaker</em> is in <em>open</em> state.
     * <p>
     * If the <em>circuit breaker</em> is in <em>open</em> state and the specified outage timeout is already passed,
     * it requests switching back to <em>closed</em> state.
     *
     * @return {@code true} if the <em>circuit breaker</em> is in <em>open</em> state, {@code false} if not.
     */
    private boolean isCircuitBreakerOpen() {
        return state.get() == State.OPEN;
    }

    /**
     * Checks whether the <em>circuit breaker</em> is in <em>closed</em> state.
     *
     * @return {@code true} if the <em>circuit breaker</em> is in <em>closed</em> state, {@code false} if not.
     */
    private boolean isCircuitBreakerClosed() {
        return state.get() == State.CLOSED;
    }

    /**
     * Checks whether the current failure attempts number reached the defined threshold.
     *
     * @return {@code true} if the current failure attempts number reached the defined threshold, {@code false} if not.
     */
    private boolean isThresholdReached() {
        return currentFailure.get().failureAttempts() >= failureAttemptsThreshold;
    }

    /**
     * Checks whether the current outage's timeout is expired.
     *
     * @return {@code true} if the current outage's timeout is expired, {@code false} if not.
     */
    private boolean isOpenStateTimeoutExpired() {
        return System.currentTimeMillis() - currentFailure.get().outageStartTime() > outageTimeout;
    }

    /**
     * Checks whether the current monitored time frame is expired.
     *
     * @return {@code true} if the current monitored time frame is expired, {@code false} if not.
     */
    private boolean isMonitoredTimeFrameExpired() {
        return System.currentTimeMillis() - currentFailure.get().monitoringStartTime() > monitoredTimeFrame;
    }

    /**
     * Internal class for the current outage's information holding.
     */
    private static class Failure {
        /**
         * Holds a current failure attempts number.
         */
        private final int failureAttempts;

        /**
         * Holds a time in milliseconds since the monitoring is active
         */
        private final long monitoringStartTime;

        /**
         * Holds a time in milliseconds when the <em>circuit breaker</em> was switched to <em>open</em> state
         */
        private final long outageStartTime;

        /**
         * Creates an immutable instance of {@code Failure} initializing it with zero failure attempts, current time
         * and zero outage start time.
         */
        Failure() {
            this(0, System.currentTimeMillis(), 0L);
        }

        /**
         * Creates an immutable instance of {@code Failure} initializing it with the current failure attempts,
         * time since the monitoring is active and outage start time.
         *
         * @param failureAttempts     current failure attempts
         * @param monitoringStartTime time since the monitoring is active
         * @param outageStartTime     time in milliseconds when the <em>circuit breaker</em> was switched to
         *                            <em>open</em> state
         */
        Failure(int failureAttempts, long monitoringStartTime, long outageStartTime) {
            this.failureAttempts = failureAttempts;
            this.monitoringStartTime = monitoringStartTime;
            this.outageStartTime = outageStartTime;
        }

        /**
         * Returns a current number of failure attempts.
         *
         * @return current number of failure attempts
         */
        int failureAttempts() {
            return failureAttempts;
        }

        /**
         * Returns a time in milliseconds since the monitoring is active.
         *
         * @return time in milliseconds since the monitoring is active
         */
        long monitoringStartTime() {
            return monitoringStartTime;
        }

        /**
         * Returns a time in milliseconds when the <em>circuit breaker</em> was switched to <em>open</em> state.
         *
         * @return time in milliseconds when the <em>circuit breaker</em> was switched to <em>open</em> state
         */
        long outageStartTime() {
            return outageStartTime;
        }

        /**
         * Increments by one the current number of failure attempts and returns a new {@code Failure} object initialized
         * with incremented number of failure attempts, the time since the monitoring is active and the time when the
         * <em>circuit breaker</em> was switched to <em>open</em> state.
         *
         * @return new {@code Failure} object initialized with incremented number of failure attempts, the time since
         * the monitoring is active and the start time of outage
         */
        Failure increment() {
            return new Failure(failureAttempts + 1, monitoringStartTime, outageStartTime);
        }
    }

    /**
     * Internal enum for <em>circuit breaker</em>'s states representation.
     */
    private enum State {
        OPEN, CLOSED
    }
}
