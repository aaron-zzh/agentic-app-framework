package com.xuejiai.aaf.framework.intelligent.team;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMemberEntity, Long> {
    List<TeamMemberEntity> findByTeamId(String teamId);
}
