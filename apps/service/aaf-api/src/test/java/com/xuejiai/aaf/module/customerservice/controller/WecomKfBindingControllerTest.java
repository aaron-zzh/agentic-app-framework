package com.xuejiai.aaf.module.customerservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.customerservice.model.entity.WecomKfAccountBinding;
import com.xuejiai.aaf.module.customerservice.repository.WecomKfAccountBindingRepository;
import com.xuejiai.aaf.test.BaseMockitoUnitTest;

/**
 * 渠道绑定是个人资源：三个接口都不得跨用户读写。
 *
 * <p>回归背景：原实现 {@code list()} 为 {@code findAll()}、{@code save()} 按 {@code openKfId} 无条件
 * upsert、{@code delete()} 为 {@code deleteById()}，同组织内任一认证用户可把他人客服账号重定向到自己指定的
 * Assistant，而渠道执行仍以原属主身份进行。
 */
class WecomKfBindingControllerTest extends BaseMockitoUnitTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_OWNER_ID = 8L;

    @Mock private WecomKfAccountBindingRepository bindingRepo;
    @Mock private AssistantExecutionService assistantExecutions;
    @Mock private OperatorContext operatorContext;

    @Test
    @DisplayName("Given 他人已占用的客服账号 When 保存绑定 Then 拒绝且不校验不落库")
    void should_reject_when_open_kf_id_owned_by_another_user() {
        // 准备参数
        var controller = controller();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(OWNER_ID));
        var existing = binding(OTHER_OWNER_ID, "open-kf-1", "assistant-of-other");
        when(bindingRepo.findByOpenKfId("open-kf-1")).thenReturn(Optional.of(existing));
        var request =
                new WecomKfBindingController.BindingRequest(
                        "open-kf-1", "客服A", "assistant-of-mine", true, null);

        // 调用 + 断言
        assertThatThrownBy(() -> controller.save(request)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(assistantExecutions);
        verify(bindingRepo, never()).save(any());
    }

    @Test
    @DisplayName("Given 本人绑定 When 保存 Then 先校验目标可执行再落库并写入归属")
    void should_validate_assistant_and_persist_owner_for_own_binding() {
        // 准备参数
        var controller = controller();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(OWNER_ID));
        when(bindingRepo.findByOpenKfId("open-kf-1")).thenReturn(Optional.empty());
        when(bindingRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 调用
        var result =
                controller.save(
                        new WecomKfBindingController.BindingRequest(
                                "open-kf-1", "客服A", "assistant-of-mine", null, "备注"));

        // 断言
        verify(assistantExecutions).requireExplicitlyExecutable("assistant-of-mine");
        assertThat(result.data().getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(result.data().getEnabled()).isTrue();
    }

    @Test
    @DisplayName("Given 他人绑定 id When 删除 Then 按不存在处理不删除")
    void should_treat_other_owner_binding_as_missing_on_delete() {
        // 准备参数
        var controller = controller();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(OWNER_ID));
        when(bindingRepo.findByIdAndOwnerId(99L, OWNER_ID)).thenReturn(Optional.empty());

        // 调用 + 断言
        assertThatThrownBy(() -> controller.delete(99L)).isInstanceOf(BusinessException.class);
        verify(bindingRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Given 当前用户 When 查询绑定 Then 只按归属列出")
    void should_list_only_own_bindings() {
        // 准备参数
        var controller = controller();
        when(operatorContext.currentOwnerId()).thenReturn(Optional.of(OWNER_ID));
        var own = binding(OWNER_ID, "open-kf-1", "assistant-of-mine");
        when(bindingRepo.findByOwnerIdOrderByIdAsc(OWNER_ID)).thenReturn(List.of(own));

        // 调用
        var result = controller.list();

        // 断言
        assertThat(result.data()).containsExactly(own);
        verify(bindingRepo, never()).findAll();
    }

    private WecomKfBindingController controller() {
        return new WecomKfBindingController(bindingRepo, assistantExecutions, operatorContext);
    }

    private static WecomKfAccountBinding binding(
            Long ownerId, String openKfId, String assistantId) {
        var binding = new WecomKfAccountBinding();
        binding.setOwnerId(ownerId);
        binding.setOpenKfId(openKfId);
        binding.setAssistantId(assistantId);
        return binding;
    }
}
