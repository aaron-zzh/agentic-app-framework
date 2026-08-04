package com.xuejiai.aaf.module.ai.aigc.execution.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.service.AigcExecutionBindingService;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingPageDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingPublishDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AIGC 执行绑定")
@RestController
@RequestMapping("/api/aigc/execution-bindings")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AigcExecutionBindingController
        extends BaseCrudController<
                AigcExecutionBinding,
                AigcExecutionBindingVO,
                AigcExecutionBindingCreateDTO,
                AigcExecutionBindingUpdateDTO,
                AigcExecutionBindingPageDTO> {

    private final AigcExecutionBindingService service;

    @Override
    protected AigcExecutionBindingService getService() {
        return service;
    }

    @PreAuthorize("hasAuthority('aigc:execution-binding:update')")
    @PostMapping("/{id}/publish")
    public Result<AigcExecutionBindingVO> publish(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody AigcExecutionBindingPublishDTO request) {
        return Result.success(service.publish(id, request.expectedVersion()));
    }
}
