package com.xuejiai.aaf.autodev.git;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Git 操作接口。 */
@Tag(name = "Git 操作")
@RestController
@RequestMapping("/api/autodev/git")
@RequiredArgsConstructor
public class GitController {

    private final GitService gitService;
    private final PullRequestService pullRequestService;

    @Operation(summary = "提交文件")
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
    @PostMapping("/branch")
    public Result<Void> createBranch(@RequestBody BranchRequest request) {
        gitService.createBranch(request.name());
        return Result.success();
    }

    @Operation(summary = "创建 Pull Request")
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
}
