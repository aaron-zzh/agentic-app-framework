package com.xuejiai.aaf.module.user.growth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.user.growth.domain.UserGrowthProgress;

public interface UserGrowthProgressRepository extends JpaRepository<UserGrowthProgress, Long> {

    List<UserGrowthProgress> findByUserIdAndDeletedFalse(Long userId);

    Optional<UserGrowthProgress> findByUserIdAndTaskIdAndDeletedFalse(Long userId, Long taskId);
}
