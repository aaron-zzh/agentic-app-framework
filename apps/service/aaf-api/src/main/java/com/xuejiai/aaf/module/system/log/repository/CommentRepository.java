package com.xuejiai.aaf.module.system.log.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.system.log.domain.Comment;

/**
 * 评论数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface CommentRepository extends CrudEntityRepository<Comment> {

    Page<Comment> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);
}
