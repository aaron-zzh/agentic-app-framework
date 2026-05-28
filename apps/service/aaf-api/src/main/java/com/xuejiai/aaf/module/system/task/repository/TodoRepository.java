package com.xuejiai.aaf.module.system.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.xuejiai.aaf.module.system.task.domain.Todo;

/**
 * 待办数据访问层。
 *
 * @author AaronZZH & Kiro
 */
public interface TodoRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {}
