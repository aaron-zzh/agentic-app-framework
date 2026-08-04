package com.xuejiai.aaf.framework.security.authorization;

import java.util.UUID;

/** AAF 唯一授权门面与 PDP。 */
public interface AuthorizationService {

    AuthorizationDecision authorize(AuthorizationRequest request);

    /** 当前认证主体是否具有精确的 ROLE_SUPER_ADMIN authority。 */
    boolean isCurrentSubjectSuperAdmin();

    boolean approveChallenge(UUID challengeId);

    /** 只读选择 APPROVED challenge 是否完整绑定当前请求，不执行消费。 */
    ContinuationSelection selectContinuation(UUID challengeId, AuthorizationRequest request);

    AuthorizationDecision resume(UUID challengeId, AuthorizationRequest request);

    /** continuation 与当前多阶段授权请求的匹配结果。 */
    enum ContinuationSelection {
        MATCH,
        NOT_MATCH,
        INDETERMINATE
    }
}
