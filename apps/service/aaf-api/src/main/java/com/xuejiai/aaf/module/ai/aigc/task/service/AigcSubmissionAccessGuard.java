package com.xuejiai.aaf.module.ai.aigc.task.service;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskTypeEnum;
import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.module.ai.aigc.ErrorCodeConstants;
import com.xuejiai.aaf.module.billing.api.MembershipProvisioningApi;

import lombok.RequiredArgsConstructor;

/** AIGC 提交会员准入守卫。 */
@Component
@RequiredArgsConstructor
public class AigcSubmissionAccessGuard {

    private final MembershipProvisioningApi membershipProvisioningApi;
    private final EntitlementChecker entitlementChecker;

    public void requireAccess(Long userId, AigcTaskTypeEnum taskType) {
        if (taskType == null) {
            throw exception(ErrorCodeConstants.AIGC_TASK_TYPE_INVALID, String.valueOf(taskType));
        }
        membershipProvisioningApi.ensureFreeSubscription(userId);
        entitlementChecker.checkBoolean(userId, entitlementCode(taskType));
    }

    private String entitlementCode(AigcTaskTypeEnum taskType) {
        return switch (taskType) {
            case IMAGE, IMAGE_PROCESS -> "aigc_image_access";
            case VIDEO -> "aigc_video_access";
            case MUSIC, VOICE -> "aigc_audio_access";
            case MODEL_3D -> "aigc_model3d_access";
        };
    }
}
