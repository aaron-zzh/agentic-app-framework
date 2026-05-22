package com.xuejiai.aaf.autodev.agent;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AutodevMessageRepository extends JpaRepository<AutodevMessage, Long> {

    List<AutodevMessage> findBySessionIdOrderByCreateTimeAsc(Long sessionId);

    List<AutodevMessage> findBySessionIdAndRoleOrderByCreateTimeAsc(Long sessionId, String role);

    List<AutodevMessage> findByDocIdOrderByCreateTimeDesc(Long docId);

    List<AutodevMessage> findByFilePathOrderByCreateTimeDesc(String filePath);
}
