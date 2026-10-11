package com.app.auth.repository;

import com.app.auth.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from RefreshSession session where session.id = :id")
    Optional<RefreshSession> findLockedById(@Param("id") UUID id);

    List<RefreshSession> findTop100ByExpiresAtBeforeOrderByExpiresAtAsc(Instant cutoff);
}
