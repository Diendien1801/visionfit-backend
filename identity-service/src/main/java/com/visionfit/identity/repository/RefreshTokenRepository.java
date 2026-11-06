package com.visionfit.identity.repository;

import com.visionfit.identity.domain.RefreshToken;
import com.visionfit.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    /** The FK cascades in Postgres as well; deleting here does not depend on how the schema was made. */
    @Modifying
    @Query("delete from RefreshToken t where t.user = :user")
    void deleteByUser(User user);
}
