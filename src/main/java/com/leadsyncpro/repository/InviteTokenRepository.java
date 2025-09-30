package com.leadsyncpro.repository;

import com.leadsyncpro.model.InviteToken;
import com.leadsyncpro.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InviteTokenRepository extends JpaRepository<InviteToken, UUID> {
    Optional<InviteToken> findByToken(String token);
    void deleteByUser(User user);
    void deleteByExpiresAtBefore(Instant now);
}
