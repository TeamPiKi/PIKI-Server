package com.depromeet.piki.common.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

// @Scheduled 등록을 프로퍼티 하나로 끌 수 있게 애플리케이션 클래스에서 분리한다.
// 통합 테스트는 scheduling.enabled=false 로 폴링을 통째로 끄고 스케줄러 진입점을 직접 호출한다 — 켜 두면
// 배경 tick 이 다른 테스트가 커밋한 큐 행을 선점해 결정적 검증이 불가능하다(#1080, 마감 종결 테스트 flaky).
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["scheduling.enabled"], havingValue = "true", matchIfMissing = true)
@EnableScheduling
class SchedulingConfig
