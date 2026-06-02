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
        return Result.success(
                orchestrator.createTeam(dto.teamId(), dto.name(), dto.mode(), dto.coordinatorId()));
    }

    @GetMapping("/{teamId}")
    public Result<TeamEntity> get(@PathVariable String teamId) {
        return Result.success(orchestrator.getTeam(teamId));
    }

    @PostMapping("/{teamId}/members")
    public Result<TeamMemberEntity> addMember(
            @PathVariable String teamId, @RequestBody MemberAddDTO dto) {
        return Result.success(
                orchestrator.addMember(teamId, dto.assistantId(), dto.role(), dto.capabilities()));
    }

    @GetMapping("/{teamId}/members")
    public Result<List<TeamMemberEntity>> listMembers(@PathVariable String teamId) {
        return Result.success(orchestrator.getMembers(teamId));
    }

    @PostMapping("/{teamId}/decompose")
    public Result<List<TeamTaskEntity>> decompose(
            @PathVariable String teamId, @RequestBody GoalDTO dto) {
        return Result.success(orchestrator.decomposeGoal(teamId, dto.goal()));
    }

    @GetMapping("/{teamId}/tasks/ready")
    public Result<List<TeamTaskEntity>> readyTasks(@PathVariable String teamId) {
        return Result.success(orchestrator.getReadyTasks(teamId));
    }

    @PutMapping("/{teamId}/tasks/{taskId}/status")
    public Result<Void> updateStatus(
            @PathVariable String teamId, @PathVariable String taskId, @RequestBody StatusDTO dto) {
        orchestrator.updateTaskStatus(teamId, taskId, dto.status(), dto.result());
        return Result.success();
    }

    record TeamCreateDTO(String teamId, String name, String mode, String coordinatorId) {}

    record MemberAddDTO(String assistantId, String role, String capabilities) {}

    record GoalDTO(String goal) {}

    record StatusDTO(String status, String result) {}
}
