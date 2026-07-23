package com.xuejiai.aaf.module.system.authorization;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthorizationChallengeRepository
        extends JpaRepository<AuthorizationChallenge, UUID> {

    @Modifying
    @Query(
            """
            update AuthorizationChallenge challenge
               set challenge.status = 'APPROVED', challenge.approvedAt = :now
             where challenge.id = :id
               and challenge.subjectId = :subjectId
               and challenge.status = 'PENDING'
               and challenge.expiresAt > :now
            """)
    int approve(
            @Param("id") UUID id,
            @Param("subjectId") Long subjectId,
            @Param("now") Instant now);

    @Query(
            """
            select challenge
              from AuthorizationChallenge challenge
             where challenge.id = :id
               and challenge.subjectId = :subjectId
               and challenge.status = 'APPROVED'
               and challenge.expiresAt > :now
            """)
    Optional<AuthorizationChallenge> findApproved(
            @Param("id") UUID id,
            @Param("subjectId") Long subjectId,
            @Param("now") Instant now);

    @Modifying
    @Query(
            """
            update AuthorizationChallenge challenge
               set challenge.status = 'CONSUMED', challenge.consumedAt = :now
             where challenge.id = :id
               and challenge.subjectId = :subjectId
               and challenge.status = 'APPROVED'
               and challenge.expiresAt > :now
            """)
    int consume(
            @Param("id") UUID id,
            @Param("subjectId") Long subjectId,
            @Param("now") Instant now);
}
