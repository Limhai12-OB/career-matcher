package com.careermatcher.cv;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
class CvAsyncConfig {

  /** Bounded pool + queue; overflow is rejected and recorded as SERVICE_BUSY. */
  @Bean(name = "cvAnalysisExecutor")
  Executor cvAnalysisExecutor(CvProperties props) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("cv-analysis-");
    executor.setCorePoolSize(props.maxConcurrentAnalyses());
    executor.setMaxPoolSize(props.maxConcurrentAnalyses());
    executor.setQueueCapacity(props.queueCapacity());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}
