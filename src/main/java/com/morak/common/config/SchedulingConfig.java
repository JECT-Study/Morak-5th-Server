package com.morak.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 애플리케이션 전체의 {@code @Scheduled} 작업을 켠다. 스케줄러는 각 도메인의 infrastructure/scheduling에 둔다. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
