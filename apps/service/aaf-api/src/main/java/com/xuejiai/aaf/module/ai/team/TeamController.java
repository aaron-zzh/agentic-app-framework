package com.xuejiai.aaf.module.ai.team;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.team.TeamOrchestrator;
import com.xuejiai.aaf.module.ai.team.vo.TeamMemberVO;
import com.xuejiai.aaf.module.ai.team.vo.TeamTaskVO;
import com.xuejiai.aaf.module.ai.team.vo.TeamVO;

import lombok.RequiredArgsConstructor;

/** 团队协作 API */
@RestController
@RequestMapping("/api/ai/teams")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TeamController {

    private final TeamOrchestrator orchestrator;

    @PostMapping
    public Result<TeamVO> create(@RequestBody TeamCreateDTO dto) {
        return Result.success(
                TeamVO.from(orchestrator.createTeam(dto.name(), dto.mode(), dto.coordinatorId())));
    }

    @GetMapping("/{teamId}")
    public Result<TeamVO> get(@PathVariable Long teamId) {
        var team = orchestrator.getTeam(teamId);
        return Result.success(team != null ? TeamVO.from(team) : null);
    }

    @PostMapping("/{teamId}/members")
    public Result<TeamMemberVO> addMember(
            @PathVariable Long teamId, @RequestBody MemberAddDTO dto) {
        return Result.success(
                TeamMemberVO.from(
                        orchestrator.addMember(
                                teamId, dto.assistantId(), dto.role(), dto.capabilities())));
    }

    @GetMapping("/{teamId}/members")
    public Result<List<TeamMemberVO>> listMembers(@PathVariable Long teamId) {
        return Result.success(
                orchestrator.getMembers(teamId).stream().map(TeamMemberVO::from).toList());
    }

    @PostMapping("/{teamId}/decompose")
    public Result<List<TeamTaskVO>> decompose(@PathVariable Long teamId, @RequestBody GoalDTO dto) {
        return Result.success(
                orchestrator.decomposeGoal(teamId, dto.goal()).stream()
                        .map(TeamTaskVO::from)
                        .toList());
    }

    @GetMapping("/{teamId}/tasks/ready")
    public Result<List<TeamTaskVO>> readyTasks(@PathVariable Long teamId) {
        return Result.success(
                orchestrator.getReadyTasks(teamId).stream().map(TeamTaskVO::from).toList());
    }

    @PutMapping("/{teamId}/tasks/{taskId}/status")
    public Result<Void> updateStatus(
            @PathVariable Long teamId, @PathVariable String taskId, @RequestBody StatusDTO dto) {
        orchestrator.updateTaskStatus(teamId, taskId, dto.status(), dto.result());
        return Result.success();
    }

    record TeamCreateDTO(String name, String mode, Long coordinatorId) {}

    record MemberAddDTO(Long assistantId, String role, String capabilities) {}

    record GoalDTO(String goal) {}

    record StatusDTO(String status, String result) {}
}
