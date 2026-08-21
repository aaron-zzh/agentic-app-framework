package com.xuejiai.aaf.framework.engine.prompt;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;

import jakarta.persistence.LockModeType;

/** 提示词稳定根对象数据访问。 */
public interface PromptTemplateRepository extends CrudEntityRepository<PromptTemplate> {

    Optional<PromptTemplate> findByCodeAndDeletedFalse(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select template from PromptTemplate template where template.code = :code")
    Optional<PromptTemplate> findByCodeForUpdate(@Param("code") String code);

    Optional<PromptTemplate> findByCodeAndVisibilityAndDeletedFalse(
            String code, PromptVisibility visibility);

    List<PromptTemplate> findAllByVisibilityAndDeletedFalse(PromptVisibility visibility);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select template from PromptTemplate template where template.id = :id")
    Optional<PromptTemplate> findByIdForUpdate(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update PromptTemplate template set template.usageCount = template.usageCount + 1"
                    + " where template.id = :id and template.deleted = false")
    int incrementUsageCount(@Param("id") Long id);
}
