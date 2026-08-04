package com.xuejiai.aaf.module.ai.aigc.execution.service;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.framework.crud.BaseCrudService;
import com.xuejiai.aaf.module.ai.aigc.execution.domain.AigcExecutionBinding;
import com.xuejiai.aaf.module.ai.aigc.execution.repository.AigcExecutionBindingRepository;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingCreateDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingPageDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingUpdateDTO;
import com.xuejiai.aaf.module.ai.aigc.execution.vo.AigcExecutionBindingVO;

import lombok.RequiredArgsConstructor;

/** 版本承载型执行绑定管理根；发布后不可原地修改。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AigcExecutionBindingService
        extends BaseCrudService<
                AigcExecutionBinding,
                AigcExecutionBindingVO,
                AigcExecutionBindingCreateDTO,
                AigcExecutionBindingUpdateDTO,
                AigcExecutionBindingPageDTO> {

    private final AigcExecutionBindingRepository repository;

    @Override
    protected AigcExecutionBindingRepository getRepository() {
        return repository;
    }

    @Override
    protected AigcExecutionBindingVO toVO(AigcExecutionBinding binding) {
        return new AigcExecutionBindingVO(
                binding.getId(),
                binding.getVersion(),
                binding.getActionKey(),
                binding.getProjectTypeCode(),
                binding.getDomainExtensionCode(),
                binding.getProductionMode(),
                binding.getChannelCode(),
                binding.getTargetType(),
                binding.getTargetRef(),
                binding.getBindingVersion(),
                binding.getPriority(),
                binding.getConfirmationRequired(),
                binding.getEstimatedCredits(),
                binding.getStatus());
    }

    @Override
    protected AigcExecutionBinding toEntity(AigcExecutionBindingCreateDTO request) {
        var binding = new AigcExecutionBinding();
        apply(
                binding,
                request.actionKey(),
                request.projectTypeCode(),
                request.domainExtensionCode(),
                request.productionMode(),
                request.channelCode(),
                request.targetType(),
                request.targetRef(),
                request.bindingVersion(),
                request.priority(),
                request.confirmationRequired(),
                request.estimatedCredits(),
                "draft");
        return binding;
    }

    @Override
    protected void updateEntity(
            AigcExecutionBinding binding, AigcExecutionBindingUpdateDTO request) {
        if ("published".equals(binding.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已发布执行绑定不可原地修改");
        }
        if (!request.expectedVersion().equals(binding.getVersion())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "执行绑定已更新，请刷新后重试");
        }
        apply(
                binding,
                request.actionKey() == null ? binding.getActionKey() : request.actionKey(),
                request.projectTypeCode() == null
                        ? binding.getProjectTypeCode()
                        : request.projectTypeCode(),
                request.domainExtensionCode() == null
                        ? binding.getDomainExtensionCode()
                        : request.domainExtensionCode(),
                request.productionMode() == null
                        ? binding.getProductionMode()
                        : request.productionMode(),
                request.channelCode() == null ? binding.getChannelCode() : request.channelCode(),
                request.targetType() == null ? binding.getTargetType() : request.targetType(),
                request.targetRef() == null ? binding.getTargetRef() : request.targetRef(),
                request.bindingVersion() == null
                        ? binding.getBindingVersion()
                        : request.bindingVersion(),
                request.priority() == null ? binding.getPriority() : request.priority(),
                request.confirmationRequired() == null
                        ? binding.getConfirmationRequired()
                        : request.confirmationRequired(),
                request.estimatedCredits() == null
                        ? binding.getEstimatedCredits()
                        : request.estimatedCredits(),
                binding.getStatus());
        binding.setVersion(binding.getVersion() + 1);
    }

    @Override
    protected void beforeDelete(AigcExecutionBinding binding) {
        if ("published".equals(binding.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "已发布执行绑定不可删除");
        }
    }

    @Transactional
    public AigcExecutionBindingVO publish(Long id, Integer expectedVersion) {
        var binding =
                repository
                        .findLockedById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "执行绑定不存在"));
        if (!"draft".equals(binding.getStatus())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "只有草稿执行绑定可以发布");
        }
        if (expectedVersion == null || !expectedVersion.equals(binding.getVersion())) {
            throw new BusinessException(GlobalErrorCode.BAD_REQUEST, "执行绑定已更新，请刷新后重试");
        }
        binding.setStatus("published");
        binding.setVersion(binding.getVersion() + 1);
        return toVO(repository.save(binding));
    }

    @Override
    protected Specification<AigcExecutionBinding> buildSpec(AigcExecutionBindingPageDTO request) {
        return SpecificationBuilder.<AigcExecutionBinding>builder()
                .eqIfPresent("actionKey", request.getActionKey())
                .eqIfPresent("targetType", request.getTargetType())
                .eqIfPresent("status", request.getStatus())
                .build();
    }

    private void apply(
            AigcExecutionBinding binding,
            String actionKey,
            String projectTypeCode,
            String domainExtensionCode,
            String productionMode,
            String channelCode,
            String targetType,
            String targetRef,
            String bindingVersion,
            Integer priority,
            Boolean confirmationRequired,
            java.math.BigDecimal estimatedCredits,
            String status) {
        binding.setActionKey(actionKey);
        binding.setProjectTypeCode(projectTypeCode);
        binding.setDomainExtensionCode(domainExtensionCode);
        binding.setProductionMode(productionMode);
        binding.setChannelCode(channelCode);
        binding.setTargetType(targetType);
        binding.setTargetRef(targetRef);
        binding.setBindingVersion(bindingVersion);
        binding.setPriority(priority == null ? 0 : priority);
        binding.setConfirmationRequired(confirmationRequired == null || confirmationRequired);
        binding.setEstimatedCredits(estimatedCredits);
        binding.setStatus(status);
    }
}
