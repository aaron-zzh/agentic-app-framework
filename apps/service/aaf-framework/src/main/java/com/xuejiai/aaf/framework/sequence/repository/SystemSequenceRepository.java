package com.xuejiai.aaf.framework.sequence.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.framework.sequence.domain.SystemSequence;

public interface SystemSequenceRepository extends JpaRepository<SystemSequence, Long> {

    Optional<SystemSequence> findByCodeAndActiveTrue(String code);
}
