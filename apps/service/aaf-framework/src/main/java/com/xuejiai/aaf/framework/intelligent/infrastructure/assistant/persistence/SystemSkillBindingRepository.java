package com.xuejiai.aaf.framework.intelligent.infrastructure.assistant.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemSkillBindingRepository
        extends JpaRepository<SystemSkillBindingEntity, Long> {

    List<SystemSkillBindingEntity> findByEnabledTrueAndDeletedFalseOrderBySortOrderAscIdAsc();
}
