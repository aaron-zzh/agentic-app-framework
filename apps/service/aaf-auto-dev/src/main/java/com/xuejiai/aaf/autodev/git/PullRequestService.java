package com.xuejiai.aaf.autodev.git;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.extern.slf4j.Slf4j;

/** GitHub Pull Request 创建服务。 */
@Slf4j
@Service
public class PullRequestService {

    @Value("${aaf.autodev.github.token:}")
    private String githubToken;

    @Value("${aaf.autodev.github.repo:}")
    private String githubRepo;

    /** 创建 Pull Request，返回 PR URL。 */
    public String createPR(String title, String body, String head, String base) {
        if (githubToken.isBlank() || githubRepo.isBlank()) {
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "GitHub 配置缺失（token 或 repo）");
        }

        var payload =
                """
                {"title":"%s","body":"%s","head":"%s","base":"%s"}
                """
                        .formatted(escape(title), escape(body), head, base);

        var request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        "https://api.github.com/repos/%s/pulls"
                                                .formatted(githubRepo)))
                        .header("Authorization", "Bearer " + githubToken)
                        .header("Accept", "application/vnd.github+json")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

        try {
            var response =
                    HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                throw new BusinessException(
                        GlobalErrorCode.INTERNAL_SERVER_ERROR,
                        "创建 PR 失败: HTTP %d - %s".formatted(response.statusCode(), response.body()));
            }
            var json = JsonUtils.readTree(response.body());
            var prUrl = json.get("html_url").asString();
            log.info("PR 创建成功：{}", prUrl);
            return prUrl;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    GlobalErrorCode.INTERNAL_SERVER_ERROR, "创建 PR 失败: " + e.getMessage());
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
