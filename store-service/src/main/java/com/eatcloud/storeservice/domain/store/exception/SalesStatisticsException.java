package com.eatcloud.storeservice.domain.store.exception;

public class SalesStatisticsException extends RuntimeException {

    public SalesStatisticsException(String message) {
        super(message);
    }

    public SalesStatisticsException(String message, Throwable cause) {
        super(message, cause);
    }

    public static class InvalidDateRangeException extends SalesStatisticsException {
        public InvalidDateRangeException(String message) {
            super(message);
        }
    }

    public static class InvalidLimitException extends SalesStatisticsException {
        public InvalidLimitException(String message) {
            super(message);
        }
    }
}