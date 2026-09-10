package com.xuejiai.aaf.module.ai.aigc.task.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.xuejiai.aaf.common.enums.aigc.AigcTaskTypeEnum;
import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.engine.entitlement.EntitlementChecker;
import com.xuejiai.aaf.module.billing.api.MembershipProvisioningApi;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/** AIGC 提交前必须先初始化 FREE，再按任务类型严格检查 BOOLEAN 权益。 */
class AigcSubmissionAccessGuardTest extends BaseMockitoUnitTest {

    @Mock private MembershipProvisioningApi membershipProvisioningApi;
    @Mock private EntitlementChecker entitlementChecker;

    @InjectMocks private AigcSubmissionAccessGuard guard;

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("accessMappings")
    @DisplayName("Given AIGC 任务类型 When 检查准入 Then 先初始化会员再检查对应 BOOLEAN 权益")
    void requireAccess_mapsTaskTypeAndPreservesOrder(
            AigcTaskTypeEnum taskType, String entitlementCode) {
        guard.requireAccess(100L, taskType);

        var ordered = inOrder(membershipProvisioningApi, entitlementChecker);
        ordered.verify(membershipProvisioningApi).ensureFreeSubscription(100L);
        ordered.verify(entitlementChecker).checkBoolean(100L, entitlementCode);
    }

    @Test
    @DisplayName("Given 任务类型为空 When 检查准入 Then 拒绝且不初始化会员")
    void requireAccess_rejectsNullTaskType() {
        assertThatThrownBy(() -> guard.requireAccess(100L, null))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(membershipProvisioningApi, entitlementChecker);
    }

    private static Stream<Arguments> accessMappings() {
        return Stream.of(
                Arguments.of(AigcTaskTypeEnum.IMAGE, "aigc_image_access"),
                Arguments.of(AigcTaskTypeEnum.IMAGE_PROCESS, "aigc_image_access"),
                Arguments.of(AigcTaskTypeEnum.VIDEO, "aigc_video_access"),
                Arguments.of(AigcTaskTypeEnum.MUSIC, "aigc_audio_access"),
                Arguments.of(AigcTaskTypeEnum.VOICE, "aigc_audio_access"),
                Arguments.of(AigcTaskTypeEnum.MODEL_3D, "aigc_model3d_access"));
    }
}
