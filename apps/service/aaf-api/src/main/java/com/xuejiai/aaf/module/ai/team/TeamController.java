package com.xuejiai.aaf.module.ai.team;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.intelligent.team.*;

import lombok.RequiredArgsConstructor;

/** 团队协作 API */
@RestController
@RequestMapping("/api/ai/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamOrchestrator orchestrator;

    @PostMapping
    public Result<TeamEntity> create(@RequestBody TeamCreateDTO dto) {
        return Result.success(orchestrator.createTeam(dto.name(), dto.mode(), dto.coordinatorId()));
    }

    @GetMapping("/{teamId}")
    public Result<TeamEntity> get(@PathVariable Long teamId) {
        return Result.success(orchestrator.getTeam(teamId));
    }

    @PostMapping("/{teamId}/members")
    public Result<TeamMemberEntity> addMember(
            @PathVariable Long teamId, @RequestBody MemberAddDTO dto) {
        return Result.success(
                orchestrator.addMember(teamId, dto.assistantId(), dto.role(), dto.capabilities()));
    }

    @GetMapping("/{teamId}/members")
    public Result<List<TeamMemberEntity>> listMembers(@PathVariable Long teamId) {
        return Result.success(orchestrator.getMembers(teamId));
    }

    @PostMapping("/{teamId}/decompose")
    public Result<List<TeamTaskEntity>> decompose(
            @PathVariable Long teamId, @RequestBody GoalDTO dto) {
        return Result.success(orchestrator.decomposeGoal(teamId, dto.goal()));
    }

    @GetMapping("/{teamId}/tasks/ready")
    public Result<List<TeamTaskEntity>> readyTasks(@PathVariable Long teamId) {
        return Result.success(orchestrator.getReadyTasks(teamId));
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
