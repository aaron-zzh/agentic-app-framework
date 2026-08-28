package com.xuejiai.aaf.module.customerservice.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.security.OperatorContext;
import com.xuejiai.aaf.module.ai.assistant.service.AssistantExecutionService;
import com.xuejiai.aaf.module.customerservice.model.entity.WecomKfAccountBinding;
import com.xuejiai.aaf.module.customerservice.repository.WecomKfAccountBindingRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/**
 * 企微客服账号绑定管理——前端通过此接口配置哪个客服账号对接哪个 Assistant。
 *
 * <p>绑定是个人资源：列表、保存、删除一律限定在当前登录用户名下，管理员也不跨用户管理。 越权与不存在统一按不存在处理；命中他人已占用的 {@code openKfId}
 * 直接拒绝（该列有唯一约束，无法退化为"新建自己的一条"）。
 *
 * <p>绑定的 {@code assistantId} 在保存时复用执行入口的可执行性规则校验，避免把不可执行的目标 留到渠道消息到达时才失败——渠道执行以绑定属主身份进行（见 {@code
 * WecomKfMessageHandler}）， 因此这里必须确认目标对属主本人可执行。
 */
@Tag(name = "企微客服配置")
@RestController
@RequestMapping("/api/wecom/kf/bindings")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aaf.wecom.kf", name = "enabled", havingValue = "true")
@PreAuthorize("isAuthenticated()")
public class WecomKfBindingController {

    private final WecomKfAccountBindingRepository bindingRepo;
    private final AssistantExecutionService assistantExecutions;
    private final OperatorContext operatorContext;

    @Operation(summary = "查询当前用户的绑定")
    @GetMapping
    public Result<List<WecomKfAccountBinding>> list() {
        return Result.success(bindingRepo.findByOwnerIdOrderByIdAsc(currentOwnerId()));
    }

    @Operation(summary = "创建/更新绑定")
    @PostMapping
    public Result<WecomKfAccountBinding> save(@Valid @RequestBody BindingRequest request) {
        var ownerId = currentOwnerId();
        var existing = bindingRepo.findByOpenKfId(request.openKfId()).orElse(null);
        if (existing != null && !ownerId.equals(existing.getOwnerId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        assistantExecutions.requireExplicitlyExecutable(request.assistantId());
        var binding = existing == null ? new WecomKfAccountBinding() : existing;
        binding.setOwnerId(ownerId);
        binding.setOpenKfId(request.openKfId());
        binding.setAccountName(request.accountName());
        binding.setAssistantId(request.assistantId());
        binding.setEnabled(request.enabled() != null ? request.enabled() : true);
        binding.setRemark(request.remark());
        return Result.success(bindingRepo.save(binding));
    }

    @Operation(summary = "删除绑定")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        var binding =
                bindingRepo
                        .findByIdAndOwnerId(id, currentOwnerId())
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        bindingRepo.delete(binding);
        return Result.success();
    }

    private Long currentOwnerId() {
        return operatorContext
                .currentOwnerId()
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.UNAUTHORIZED));
    }

    public record BindingRequest(
            @NotBlank String openKfId,
            String accountName,
            @NotBlank String assistantId,
            Boolean enabled,
            String remark) {}
}
