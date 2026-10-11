package com.app.auth.repository;

import com.app.auth.entity.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Query("select token.session.id from RefreshToken token where token.tokenHash = :hash")
    Optional<UUID> findSessionIdByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from RefreshToken token where token.session.id = :id")
    void deleteForSession(@Param("id") UUID id);
}
