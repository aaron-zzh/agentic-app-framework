package com.xuejiai.aaf.module.ai.flow.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.xuejiai.aaf.common.model.Result;
import com.xuejiai.aaf.framework.crud.BaseCrudController;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.flow.domain.AiFlowDefinition;
import com.xuejiai.aaf.module.ai.flow.service.AiFlowService;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionCreateDTO;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionPageDTO;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionUpdateDTO;
import com.xuejiai.aaf.module.ai.flow.vo.AiFlowDefinitionVO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "AI 工作流管理")
@RestController
@RequestMapping("/api/ai/workflows")
@RequiredArgsConstructor
public class AiFlowController
        extends BaseCrudController<
                AiFlowDefinition,
                AiFlowDefinitionVO,
                AiFlowDefinitionCreateDTO,
                AiFlowDefinitionUpdateDTO,
                AiFlowDefinitionPageDTO> {

    private final AiFlowService service;

    @Override
    protected BaseCrudService<
                    AiFlowDefinition,
                    AiFlowDefinitionVO,
                    AiFlowDefinitionCreateDTO,
                    AiFlowDefinitionUpdateDTO,
                    AiFlowDefinitionPageDTO>
            getService() {
        return service;
    }

    /** 发布工作流：服务端编译已保存定义并部署到 Flowable 引擎。 */
    @PostMapping("/{id}/deploy")
    public Result<AiFlowDefinitionVO> deploy(@PathVariable Long id) {
        return Result.success(service.deploy(id));
    }
}
