package com.aicoach.common.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background work: scheduled jobs (sweeper, embedding backfill) and a virtual-thread executor for AI generation. */
@Configuration(proxyBeanMethods = false)
public class SchedulingConfig {

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Enabled {
    }

    /** Interview side effects (streaming generation, assessment, evaluation) run here, off the request thread. */
    @Bean(destroyMethod = "close")
    ExecutorService interviewExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
