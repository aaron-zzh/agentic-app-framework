package com.xuejiai.aaf.framework.intelligent.ai.safety;

/** 生成式内容安全审查结果。 */
public record ContentSafetyResult(
        boolean allowed, String code, String message, boolean reviewRequired, String reviewId) {

    public static ContentSafetyResult pass() {
        return new ContentSafetyResult(true, "OK", "审查通过", false, null);
    }

    public static ContentSafetyResult rejected(String code, String message) {
        return new ContentSafetyResult(false, code, message, false, null);
    }

    public static ContentSafetyResult pendingReview(String reviewId, String message) {
        return new ContentSafetyResult(false, "PENDING_CONTENT_REVIEW", message, true, reviewId);
    }
}
