package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;

import jakarta.persistence.LockModeType;

/** 统一提示词资产数据访问。 */
public interface PromptTemplateRepository extends CrudEntityRepository<PromptTemplate> {

    Optional<PromptTemplate> findByNameAndActiveTrueAndVisibility(String name, String visibility);

    Optional<PromptTemplate> findByNameAndTemplateVersionAndVisibility(
            String name, Integer templateVersion, String visibility);

    List<PromptTemplate> findByNameAndVisibilityOrderByTemplateVersionDesc(
            String name, String visibility);

    List<PromptTemplate> findByCategoryAndVisibility(String category, String visibility);

    Optional<PromptTemplate> findByIdAndVisibility(Long id, String visibility);

    List<PromptTemplate> findAllByVisibility(String visibility);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select template from PromptTemplate template where template.id = :id")
    Optional<PromptTemplate> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update PromptTemplate template set template.usageCount = template.usageCount + 1"
                    + " where template.id = :id and template.deleted = false")
    int incrementUsageCount(@Param("id") Long id);
}
