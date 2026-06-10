package com.xuejiai.aaf.module.customerservice.controller;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.module.customerservice.model.entity.WecomKfAccountBinding;
import com.xuejiai.aaf.module.customerservice.repository.WecomKfAccountBindingRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

/** 企微客服账号绑定管理——前端通过此接口配置哪个客服账号对接哪个 Assistant */
@Tag(name = "企微客服配置")
@RestController
@RequestMapping("/api/wecom/kf/bindings")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aaf.wecom.kf", name = "enabled", havingValue = "true")
public class WecomKfBindingController {

    private final WecomKfAccountBindingRepository bindingRepo;

    @Operation(summary = "查询所有绑定")
    @GetMapping
    public Result<List<WecomKfAccountBinding>> list() {
        return Result.success(bindingRepo.findAll());
    }

    @Operation(summary = "创建/更新绑定")
    @PostMapping
    public Result<WecomKfAccountBinding> save(@Valid @RequestBody BindingRequest request) {
        var binding =
                bindingRepo
                        .findByOpenKfId(request.openKfId())
                        .orElseGet(WecomKfAccountBinding::new);
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
        bindingRepo.deleteById(id);
        return Result.success();
    }

    public record BindingRequest(
            @NotBlank String openKfId,
            String accountName,
            @jakarta.validation.constraints.NotNull Long assistantId,
            Boolean enabled,
            String remark) {}
}
