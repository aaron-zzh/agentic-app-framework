package com.xuejiai.aaf.module.system.log.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.log.domain.Comment;

/**
 * 评论数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface CommentRepository
        extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {

    Page<Comment> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);
}
