package com.eatcloud.orderservice.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Stack;
import java.util.UUID;

@Slf4j
@Getter
public class SagaTransaction {
    private final String transactionId;
    private final Stack<CompensationStep> compensations = new Stack<>();
    private boolean completed = false;
    
    public SagaTransaction() {
        this.transactionId = UUID.randomUUID().toString();
    }
    
    public SagaTransaction(String transactionId) {
        this.transactionId = transactionId;
    }

    public void addCompensation(String name, Runnable compensation) {
        compensations.push(new CompensationStep(name, compensation));
        log.debug("Added compensation step '{}' to saga transaction: {}", name, transactionId);
    }
    public void complete() {
        this.completed = true;
        log.info("Saga transaction completed successfully: {}", transactionId);
    }

    public void compensate() {
        if (completed) {
            log.warn("Attempted to compensate completed transaction: {}", transactionId);
            return;
        }
        
        log.info("Starting compensation for saga transaction: {}", transactionId);
        int totalSteps = compensations.size();
        int currentStep = 0;
        
        while (!compensations.isEmpty()) {
            currentStep++;
            CompensationStep step = compensations.pop();
            
            try {
                log.info("Executing compensation step {}/{}: '{}' for transaction: {}", 
                        currentStep, totalSteps, step.name, transactionId);
                step.compensation.run();
                log.info("Compensation step '{}' completed successfully", step.name);
                
            } catch (Exception e) {
                log.error("Compensation step '{}' failed for transaction: {}. Error: {}", 
                         step.name, transactionId, e.getMessage(), e);
                recordFailedCompensation(step.name, e);
            }
        }
        
        log.info("Compensation completed for saga transaction: {} ({} steps processed)", 
                transactionId, totalSteps);
    }

    private void recordFailedCompensation(String stepName, Exception e) {
        // TODO: 실패한 보상을 DB나 메시지 큐에 기록
        log.error("MANUAL_INTERVENTION_REQUIRED: Failed compensation step '{}' for transaction '{}': {}", 
                 stepName, transactionId, e.getMessage());
    }

    private static class CompensationStep {
        private final String name;
        private final Runnable compensation;
        
        public CompensationStep(String name, Runnable compensation) {
            this.name = name;
            this.compensation = compensation;
        }
    }
}
