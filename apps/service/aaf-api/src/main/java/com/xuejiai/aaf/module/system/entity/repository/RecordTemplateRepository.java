package com.xuejiai.aaf.module.system.entity.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.xuejiai.aaf.module.system.entity.domain.RecordTemplate;

/**
 * 记录模板仓储。
 *
 * @author AaronZZH & Kiro
 */
public interface RecordTemplateRepository extends JpaRepository<RecordTemplate, Long> {

    /** 查询某实体下当前用户或共享的模板 */
    List<RecordTemplate> findByEntitySlugAndCreateByOrEntitySlugAndIsSharedTrue(
            String slug1, Long createBy, String slug2);

    /** 清除某实体下某用户的默认标记 */
    @Modifying
    @Query(
            "UPDATE RecordTemplate t SET t.isDefault = false "
                    + "WHERE t.entitySlug = :slug AND t.createBy = :userId AND t.isDefault = true")
    void clearDefault(String slug, Long userId);
}
