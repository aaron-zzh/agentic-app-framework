package com.xuejiai.aaf.module.company.okr.repository;

import java.util.List;

import com.xuejiai.aaf.module.company.okr.domain.KeyResult;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KeyResultRepository extends JpaRepository<KeyResult, Long> {

    List<KeyResult> findByObjectiveId(Long objectiveId);
}
