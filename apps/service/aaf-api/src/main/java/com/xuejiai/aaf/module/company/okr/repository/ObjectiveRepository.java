package com.xuejiai.aaf.module.company.okr.repository;

import java.util.List;

import com.xuejiai.aaf.module.company.okr.domain.Objective;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ObjectiveRepository extends JpaRepository<Objective, Long> {

    List<Objective> findByPeriod(String period);

    List<Objective> findByOwnerUserId(Long ownerUserId);
}
