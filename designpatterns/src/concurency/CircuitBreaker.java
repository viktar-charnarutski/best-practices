package concurency;

/**
 * Circuit Breaker API.
 */
public interface CircuitBreaker {

    /**
     * Opens the <em>Circuit Breaker</em>.
     */
    void openCircuitBreaker();

    /**
     * Closes the <em>Circuit Breaker</em>.
     */
    void closeCircuitBreaker();

    /**
     * Checks if the <em>Circuit Breaker</em> is in <em>open</em> state.
     */
    boolean isCircuitBreakerOpen();

    /**
     * Checks if the <em>Circuit Breaker</em> is in <em>closed</em> state.
     */
    boolean isCircuitBreakerClosed();
}
