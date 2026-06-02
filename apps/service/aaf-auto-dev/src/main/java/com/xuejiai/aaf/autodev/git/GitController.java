package com.xuejiai.aaf.autodev.git;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Git + CI/CD 操作接口。 */
@Tag(name = "Git & CI/CD")
@RestController
@RequestMapping("/api/autodev/git")
@RequiredArgsConstructor
public class GitController {

    private final GitService gitService;
    private final PullRequestService pullRequestService;
    private final CiCdService ciCdService;

    @Operation(summary = "提交文件")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/commit")
    public Result<String> commit(@RequestBody CommitRequest request) {
        return Result.success(gitService.commit(request.message(), request.files()));
    }

    @Operation(summary = "查看工作区变更")
    @GetMapping("/diff")
    public Result<String> diff() {
        return Result.success(gitService.diff());
    }

    @Operation(summary = "获取提交历史")
    @GetMapping("/log")
    public Result<List<String>> log(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(gitService.log(limit));
    }

    @Operation(summary = "创建分支")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/branch")
    public Result<Void> createBranch(@RequestBody BranchRequest request) {
        gitService.createBranch(request.name());
        return Result.success();
    }

    @Operation(summary = "创建 Pull Request")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/pr")
    public Result<String> createPR(@RequestBody PullRequestRequest request) {
        var url =
                pullRequestService.createPR(
                        request.title(), request.body(), request.head(), request.base());
        return Result.success(url);
    }

    /** 提交请求。 */
    record CommitRequest(String message, List<String> files) {}

    /** 分支请求。 */
    record BranchRequest(String name) {}

    /** PR 请求。 */
    record PullRequestRequest(String title, String body, String head, String base) {}

    // ===== CI/CD =====

    @Operation(summary = "触发 CI Pipeline")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/ci/trigger")
    public Result<Long> triggerCi(@RequestBody CiTriggerRequest request) {
        var runId =
                ciCdService.triggerWorkflow(request.workflow(), request.ref(), request.inputs());
        return Result.success(runId);
    }

    @Operation(summary = "查询构建状态")
    @GetMapping("/ci/status/{runId}")
    public Result<CiCdService.BuildStatus> ciStatus(@PathVariable Long runId) {
        return Result.success(ciCdService.getStatus(runId));
    }

    @Operation(summary = "最近构建列表")
    @GetMapping("/ci/recent")
    public Result<List<CiCdService.BuildStatus>> recentBuilds(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(ciCdService.recentBuilds(limit));
    }

    @Operation(summary = "触发部署")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/ci/deploy")
    public Result<Long> deploy(@RequestBody DeployRequest request) {
        var runId = ciCdService.triggerDeploy(request.environment(), request.ref());
        return Result.success(runId);
    }

    @Operation(summary = "GitHub Webhook 回调")
    @PostMapping("/webhook/github")
    public Result<Void> githubWebhook(
            @RequestHeader("X-GitHub-Event") String event, @RequestBody JsonNode payload) {
        ciCdService.handleWebhook(event, payload);
        return Result.success();
    }

    record CiTriggerRequest(String workflow, String ref, Map<String, String> inputs) {}

    record DeployRequest(String environment, String ref) {}
}
