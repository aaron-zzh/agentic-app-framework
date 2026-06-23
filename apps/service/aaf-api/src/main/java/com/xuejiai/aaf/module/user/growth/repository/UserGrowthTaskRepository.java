package com.xuejiai.aaf.module.user.growth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.user.growth.domain.UserGrowthTask;

public interface UserGrowthTaskRepository extends JpaRepository<UserGrowthTask, Long> {

    List<UserGrowthTask> findByEnabledAndDeletedFalseOrderBySortOrderAsc(Boolean enabled);
}
