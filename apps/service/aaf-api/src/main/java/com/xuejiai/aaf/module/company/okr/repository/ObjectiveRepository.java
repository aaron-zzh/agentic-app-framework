package com.xuejiai.aaf.module.company.okr.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.company.okr.domain.Objective;

public interface ObjectiveRepository extends JpaRepository<Objective, Long> {

    List<Objective> findByPeriod(String period);

    List<Objective> findByOwnerUserId(Long ownerUserId);
}
