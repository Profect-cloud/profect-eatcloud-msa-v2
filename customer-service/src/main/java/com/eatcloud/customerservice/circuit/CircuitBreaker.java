package com.eatcloud.customerservice.circuit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class CircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> lastFailureTime = new AtomicReference<>();

    private final int failureThreshold;
    private final int successThreshold;
    private final long timeoutDuration;

    public CircuitBreaker() {
        this(5, 3, 60000);
    }

    public CircuitBreaker(int failureThreshold, int successThreshold, long timeoutDuration) {
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.timeoutDuration = timeoutDuration;
    }

    public boolean canExecute() {
        State currentState = state.get();
        
        switch (currentState) {
            case CLOSED:
                return true;
            case OPEN:
                if (shouldAttemptReset()) {
                    log.info("Circuit Breaker 상태를 HALF_OPEN으로 변경");
                    state.set(State.HALF_OPEN);
                    return true;
                }
                log.warn("Circuit Breaker가 OPEN 상태입니다. 요청이 차단됩니다.");
                return false;
            case HALF_OPEN:
                return true;
            default:
                return false;
        }
    }

    public void onSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            int success = successCount.incrementAndGet();
            log.debug("HALF_OPEN 상태에서 성공 횟수: {}/{}", success, successThreshold);
            
            if (success >= successThreshold) {
                log.info("Circuit Breaker 상태를 CLOSED로 변경");
                state.set(State.CLOSED);
                resetCounters();
            }
        }
    }

    public void onFailure(Exception exception) {
        State currentState = state.get();
        
        if (currentState == State.CLOSED) {
            int failure = failureCount.incrementAndGet();
            lastFailureTime.set(LocalDateTime.now());
            log.warn("CLOSED 상태에서 실패 횟수: {}/{}", failure, failureThreshold);
            
            if (failure >= failureThreshold) {
                log.error("Circuit Breaker 상태를 OPEN으로 변경. 실패 임계값 도달");
                state.set(State.OPEN);
            }
        } else if (currentState == State.HALF_OPEN) {
            log.error("HALF_OPEN 상태에서 실패 발생. Circuit Breaker를 OPEN으로 변경");
            state.set(State.OPEN);
            resetCounters();
        }
    }

    private boolean shouldAttemptReset() {
        LocalDateTime lastFailure = lastFailureTime.get();
        if (lastFailure == null) {
            return false;
        }
        
        long elapsed = java.time.Duration.between(lastFailure, LocalDateTime.now()).toMillis();
        return elapsed >= timeoutDuration;
    }

    private void resetCounters() {
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(null);
    }

    public State getCurrentState() {
        return state.get();
    }

    public void logStatus() {
        State currentState = state.get();
        log.info("Circuit Breaker 상태: {}, 실패 횟수: {}, 성공 횟수: {}", 
                currentState, failureCount.get(), successCount.get());
    }

    public void reset() {
        log.info("Circuit Breaker를 수동으로 리셋합니다.");
        state.set(State.CLOSED);
        resetCounters();
    }
}
