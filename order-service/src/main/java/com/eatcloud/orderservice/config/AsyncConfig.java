package com.eatcloud.orderservice.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        
        // ⭐ MDC 전파를 위한 TaskDecorator 설정
        executor.setTaskDecorator(new MDCTaskDecorator());
        
        executor.initialize();
        return executor;
    }

    // MDC를 새로운 스레드로 전파하는 TaskDecorator
    public static class MDCTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            // 현재 스레드의 MDC 복사
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            
            return () -> {
                try {
                    // 새 스레드에서 MDC 복원
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    // 작업 완료 후 MDC 정리
                    MDC.clear();
                }
            };
        }
    }
}
