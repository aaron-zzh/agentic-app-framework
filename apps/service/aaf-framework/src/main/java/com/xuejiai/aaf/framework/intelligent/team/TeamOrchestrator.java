package com.xuejiai.aaf.framework.intelligent.team;

import java.util.List;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;
import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient.LlmMessage;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 团队协作编排器——团队 CRUD + 协作规范 + LLM 任务拆解。
 *
 * <p>持久化委托 TeamRepository（JPA），运行时通过 DB 查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamOrchestrator {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository memberRepository;
    private final TeamTaskRepository taskRepository;
    private final LlmClient llmClient;

    // ===== CRUD =====

    /** 创建团队 */
    public TeamEntity createTeam(String teamId, String name, String mode, String coordinatorId) {
        var team = new TeamEntity();
        team.setTeamId(teamId);
        team.setName(name);
        team.setCollaborationMode(mode);
        team.setCoordinatorAssistantId(coordinatorId);
        return teamRepository.save(team);
    }

    /** 获取团队 */
    public TeamEntity getTeam(String teamId) {
        return teamRepository.findByTeamId(teamId).orElse(null);
    }

    /** 添加成员 */
    public TeamMemberEntity addMember(String teamId, String assistantId, String role, String capabilities) {
        var member = new TeamMemberEntity();
        member.setTeamId(teamId);
        member.setAssistantId(assistantId);
        member.setRole(role);
        member.setCapabilities(capabilities);
        return memberRepository.save(member);
    }

    /** 获取团队成员 */
    public List<TeamMemberEntity> getMembers(String teamId) {
        return memberRepository.findByTeamId(teamId);
    }

    /** 获取协调者 */
    public String getCoordinator(String teamId) {
        var team = teamRepository.findByTeamId(teamId).orElse(null);
        return team != null ? team.getCoordinatorAssistantId() : null;
    }

    // ===== LLM 任务拆解 =====

    private static final String DECOMPOSE_PROMPT = """
            将以下目标拆解为子任务，返回 JSON 数组（不要其他内容）：
            [{"taskId":"t1","description":"描述","requiredCapability":"能力","dependencies":[],"priority":0}]

            团队成员能力：%s
            目标：%s""";

    /** LLM 驱动任务拆解 */
    public List<TeamTaskEntity> decomposeGoal(String teamId, String goal) {
        var members = getMembers(teamId);
        var capabilities = members.stream()
                .map(m -> m.getAssistantId() + ":" + (m.getCapabilities() != null ? m.getCapabilities() : "general"))
                .toList();

        try {
            var prompt = DECOMPOSE_PROMPT.formatted(capabilities, goal);
            var response = llmClient.call(List.of(LlmMessage.user(prompt)), "task_decompose", null);
            return parseAndSaveTasks(teamId, response);
        } catch (Exception e) {
            log.warn("LLM 任务拆解失败: {}", e.getMessage());
            // 降级：整个目标作为单一任务
            var task = new TeamTaskEntity();
            task.setTeamId(teamId);
            task.setTaskId("t1");
            task.setDescription(goal);
            task.setStatus("PENDING");
            return List.of(taskRepository.save(task));
        }
    }

    // ===== DAG 执行 =====

    /** 获取当前可执行的任务（依赖已满足） */
    public List<TeamTaskEntity> getReadyTasks(String teamId) {
        var allTasks = taskRepository.findByTeamId(teamId);
        var completedIds = allTasks.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .map(TeamTaskEntity::getTaskId)
                .toList();

        return allTasks.stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .filter(t -> {
                    var deps = t.getDependencies();
                    if (deps == null || deps.isBlank()) return true;
                    var depList = List.of(deps.split(","));
                    return completedIds.containsAll(depList);
                })
                .toList();
    }

    /** 更新任务状态 */
    public void updateTaskStatus(String teamId, String taskId, String status, String result) {
        taskRepository.findByTeamIdAndTaskId(teamId, taskId).ifPresent(task -> {
            task.setStatus(status);
            if (result != null) task.setResult(result);
            taskRepository.save(task);
        });
    }

    private List<TeamTaskEntity> parseAndSaveTasks(String teamId, String json) {
        var tasks = new java.util.ArrayList<TeamTaskEntity>();
        // 简单解析 JSON 数组中的对象
        var pattern = java.util.regex.Pattern.compile(
                "\"taskId\":\"([^\"]+)\".*?\"description\":\"([^\"]+)\".*?\"requiredCapability\":\"([^\"]*?)\".*?\"priority\":(\\d+)");
        var matcher = pattern.matcher(json);
        while (matcher.find()) {
            var task = new TeamTaskEntity();
            task.setTeamId(teamId);
            task.setTaskId(matcher.group(1));
            task.setDescription(matcher.group(2));
            task.setRequiredCapability(matcher.group(3));
            task.setPriority(Integer.parseInt(matcher.group(4)));
            task.setStatus("PENDING");
            tasks.add(taskRepository.save(task));
        }
        return tasks;
    }
}
