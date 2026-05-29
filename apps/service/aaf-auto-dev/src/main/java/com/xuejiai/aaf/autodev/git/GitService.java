package com.xuejiai.aaf.autodev.git;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.extern.slf4j.Slf4j;

/** Git 操作封装服务，基于 JGit。 */
@Slf4j
@Service
public class GitService {

    @Value("${aaf.autodev.git.repo-path:.}")
    private String repoPath;

    @Value("${aaf.autodev.github.token:}")
    private String githubToken;

    /** 初始化 Git 仓库。 */
    public void init(String path) {
        try {
            Git.init().setDirectory(new File(path)).call().close();
            log.info("Git 仓库初始化完成：{}", path);
        } catch (GitAPIException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "Git 初始化失败: " + e.getMessage());
        }
    }

    /** 提交指定文件。 */
    public String commit(String message, List<String> files) {
        try (var git = openRepo()) {
            var addCmd = git.add();
            files.forEach(addCmd::addFilepattern);
            addCmd.call();

            var commit = git.commit().setMessage(message).call();
            log.info("Git 提交完成：{}", commit.getId().getName());
            return commit.getId().getName();
        } catch (GitAPIException | IOException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "Git 提交失败: " + e.getMessage());
        }
    }

    /** 创建分支。 */
    public void createBranch(String name) {
        try (var git = openRepo()) {
            git.branchCreate().setName(name).call();
            log.info("分支创建完成：{}", name);
        } catch (GitAPIException | IOException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "创建分支失败: " + e.getMessage());
        }
    }

    /** 推送到远程。 */
    public void push(String remote, String branch) {
        try (var git = openRepo()) {
            var pushCmd = git.push().setRemote(remote).add(branch);
            if (!githubToken.isBlank()) {
                pushCmd.setCredentialsProvider(
                        new UsernamePasswordCredentialsProvider(githubToken, ""));
            }
            pushCmd.call();
            log.info("推送完成：{}/{}", remote, branch);
        } catch (GitAPIException | IOException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "Git 推送失败: " + e.getMessage());
        }
    }

    /** 获取工作区变更。 */
    public String diff() {
        try (var git = openRepo()) {
            var repo = git.getRepository();
            var out = new ByteArrayOutputStream();
            try (ObjectReader reader = repo.newObjectReader();
                    var formatter = new DiffFormatter(out)) {
                formatter.setRepository(repo);
                var headTree = new CanonicalTreeParser();
                var headCommit = repo.resolve("HEAD^{tree}");
                if (headCommit != null) {
                    headTree.reset(reader, headCommit);
                }
                var diffs = formatter.scan(headTree, new CanonicalTreeParser());
                for (DiffEntry entry : diffs) {
                    formatter.format(entry);
                }
            }
            return out.toString();
        } catch (IOException | GitAPIException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "获取 diff 失败: " + e.getMessage());
        }
    }

    /** 获取提交历史。 */
    public List<String> log(int limit) {
        try (var git = openRepo()) {
            var logCmd = git.log().setMaxCount(limit);
            var commits = logCmd.call();
            var result = new java.util.ArrayList<String>();
            for (RevCommit commit : commits) {
                result.add(
                        "%s %s"
                                .formatted(
                                        commit.getId().abbreviate(8).name(),
                                        commit.getShortMessage()));
            }
            return result;
        } catch (GitAPIException | IOException e) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "获取日志失败: " + e.getMessage());
        }
    }

    private Git openRepo() throws IOException, GitAPIException {
        return Git.open(new File(repoPath));
    }
}
