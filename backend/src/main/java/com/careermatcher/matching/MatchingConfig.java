package com.careermatcher.matching;

import com.careermatcher.matching.engine.MatchingEngine;
import com.careermatcher.matching.engine.MatchingWeights;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MatchingConfig {

  @Bean
  MatchingEngine matchingEngine(MatchingWeights weights) {
    return new MatchingEngine(weights);
  }
}
