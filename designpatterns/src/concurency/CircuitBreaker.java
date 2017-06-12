package concurency;

/**
 * Circuit Breaker API.
 */
public interface CircuitBreaker {

    void openCircuitBreaker();

    void closeCircuitBreaker();

    boolean isCircuitBreakerOpen();

    boolean isCircuitBreakerClosed();
}
