package com.xuejiai.aaf.module.system.dashboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.dashboard.domain.PageDef;

/**
 * 页面定义 Repository。
 *
 * @author AaronZZH & Kiro
 */
public interface PageDefRepository extends JpaRepository<PageDef, Long> {

    List<PageDef> findAllByDeletedFalse();

    Optional<PageDef> findByIdAndDeletedFalse(Long id);

    Optional<PageDef> findBySlugAndStatusAndDeletedFalse(String slug, String status);

    boolean existsBySlugAndDeletedFalse(String slug);
}
