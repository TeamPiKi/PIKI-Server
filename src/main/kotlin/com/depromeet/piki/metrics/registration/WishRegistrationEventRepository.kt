package com.depromeet.piki.metrics.registration

import org.springframework.data.jpa.repository.JpaRepository

interface WishRegistrationEventRepository : JpaRepository<WishRegistrationEvent, Long>
