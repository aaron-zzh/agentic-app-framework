package com.xuejiai.aaf.framework.intelligent.ai.safety;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService;
import com.xuejiai.aaf.framework.intelligent.assistant.hitl.HumanApprovalService.ApprovalResolvedEvent;

import lombok.RequiredArgsConstructor;

/** 默认审查实现：普通请求放行；高风险生成可转入统一 HITL 内容复审。 */
@Component
@RequiredArgsConstructor
@ConditionalOnMissingBean(ContentSafetyService.class)
public class NoopContentSafetyService implements ContentSafetyService {

    private final HumanApprovalService approvalService;
    private final Map<String, ReviewState> reviewStates = new ConcurrentHashMap<>();

    @Override
    public ContentSafetyResult reviewBeforeGeneration(ContentSafetyRequest request) {
        if (requiresHumanReview(request)) {
            var reviewKey = reviewKey(request);
            var state = reviewStates.get(reviewKey);
            if (state != null) {
                return switch (state.decision()) {
                    case APPROVED -> ContentSafetyResult.pass();
                    case REJECTED -> ContentSafetyResult.rejected("CONTENT_REVIEW_REJECTED", "内容安全复审未通过");
                    case PENDING -> ContentSafetyResult.pendingReview(state.approvalId(), "生成内容正在等待人工复审");
                };
            }
            var reviewId =
                    approvalService.request(
                            request.sessionId(),
                            request.userId(),
                            HumanApprovalService.ApprovalType.CONTENT_REVIEW,
                            "生成内容安全复审",
                            "AI 生成内容需要人工复审后继续执行",
                            java.util.Map.of(
                                    "subjectType",
                                    "CONTENT",
                                    "subjectKey",
                                    value(request.toolName()),
                                    "toolName",
                                    value(request.toolName()),
                                    "category",
                                    request.category() == null ? "" : request.category(),
                                    "prompt",
                                    request.prompt() == null ? "" : request.prompt(),
                                    "contentReviewKey",
                                    reviewKey,
                                    "riskLevel",
                                    stringMeta(request, "riskLevel"),
                                    "grantScope",
                                    "ONCE"));
            reviewStates.put(reviewKey, new ReviewState(ReviewDecision.PENDING, reviewId));
            return ContentSafetyResult.pendingReview(reviewId, "生成内容需要人工复审");
        }
        return ContentSafetyResult.pass();
    }

    /** 内容复审完成后记录结果；AI 用相同参数重试时可继续执行或得到稳定拒绝。 */
    @EventListener
    public void onApprovalResolved(ApprovalResolvedEvent event) {
        if (event.request().type() != HumanApprovalService.ApprovalType.CONTENT_REVIEW) {
            return;
        }
        var key = stringContext(event.request().context(), "contentReviewKey");
        if (key == null || key.isBlank()) {
            return;
        }
        var decision =
                event.result().decision() == HumanApprovalService.Decision.APPROVED
                        ? ReviewDecision.APPROVED
                        : ReviewDecision.REJECTED;
        reviewStates.put(key, new ReviewState(decision, event.request().requestId()));
    }

    private boolean requiresHumanReview(ContentSafetyRequest request) {
        if (request == null || request.metadata() == null) {
            return false;
        }
        var value = request.metadata().get("requireHumanReview");
        return value instanceof Boolean bool && bool;
    }

    private String stringMeta(ContentSafetyRequest request, String key) {
        if (request.metadata() == null) {
            return "";
        }
        var value = request.metadata().get(key);
        return value == null ? "" : value.toString();
    }

    private String reviewKey(ContentSafetyRequest request) {
        var raw =
                "%s|%s|%s|%s"
                        .formatted(
                                value(request.sessionId()),
                                request.userId() == null ? "" : request.userId(),
                                value(request.toolName()),
                                value(request.prompt()));
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private static String stringContext(Map<String, Object> context, String key) {
        if (context == null) {
            return null;
        }
        var value = context.get(key);
        return value == null ? null : value.toString();
    }

    private enum ReviewDecision {
        PENDING,
        APPROVED,
        REJECTED
    }

    private record ReviewState(ReviewDecision decision, String approvalId) {}
}
