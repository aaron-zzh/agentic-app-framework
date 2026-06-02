package com.xuejiai.aaf.module.company.okr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.okr.domain.KeyResult;

public interface KeyResultRepository extends JpaRepository<KeyResult, Long> {

    List<KeyResult> findByObjectiveId(Long objectiveId);
}
