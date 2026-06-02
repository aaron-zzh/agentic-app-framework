package com.xuejiai.aaf.framework.intelligent.team;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamTaskRepository extends JpaRepository<TeamTaskEntity, Long> {
    List<TeamTaskEntity> findByTeamId(String teamId);

    Optional<TeamTaskEntity> findByTeamIdAndTaskId(String teamId, String taskId);
}
