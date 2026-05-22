package com.xuejiai.aaf.autodev.agent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutodevSessionRepository extends JpaRepository<AutodevSession, Long> {

    List<AutodevSession> findTop20ByOrderByCreateTimeDesc();
}
