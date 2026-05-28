/**
 * 访问策略管理 Service（ABAC 层）。
 *
 * @author AaronZZH & Kiro
 */
package com.xuejiai.aaf.module.system.role.policy;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccessPolicyService {

    private final AccessPolicyRepository repository;

    /**
     * 创建策略
     *
     * @param dto 创建请求
     * @return 策略信息
     */
    @Transactional
    public AccessPolicyVO create(AccessPolicyCreateDTO dto) {
        var entity = new AccessPolicy();
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setConditionJson(dto.conditionJson());
        entity.setEffect(dto.effect());
        entity.setPriority(dto.priority() != null ? dto.priority() : 100);
        entity.setTargetResource(dto.targetResource());
        entity.setTargetAction(dto.targetAction());
        return toVO(repository.save(entity));
    }

    /**
     * 更新策略
     *
     * @param id 策略编号
     * @param dto 更新请求
     * @return 更新后的策略信息
     */
    @Transactional
    public AccessPolicyVO update(Long id, AccessPolicyCreateDTO dto) {
        var entity = getEntity(id);
        entity.setName(dto.name());
        entity.setDescription(dto.description());
        entity.setConditionJson(dto.conditionJson());
        entity.setEffect(dto.effect());
        if (dto.priority() != null) entity.setPriority(dto.priority());
        entity.setTargetResource(dto.targetResource());
        entity.setTargetAction(dto.targetAction());
        return toVO(repository.save(entity));
    }

    /**
     * 删除策略
     *
     * @param id 策略编号
     */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    /**
     * 查询所有启用策略
     *
     * @return 策略列表
     */
    public List<AccessPolicyVO> listEnabled() {
        return repository.findByStatusOrderByPriority(1).stream().map(this::toVO).toList();
    }

    /**
     * 策略测试——评估给定上下文是否通过策略
     *
     * @param dto 测试请求
     * @return 测试结果
     */
    public PolicyTestResultVO test(PolicyTestDTO dto) {
        var policies = repository.findByTargetResourceAndStatusOrderByPriority(dto.resourceType(), 1);
        for (var policy : policies) {
            // TODO: 解析 conditionJson 并评估 dto.context()
            // 当前骨架：所有策略默认通过
        }
        return new PolicyTestResultVO(true, "所有策略通过（评估引擎待实现）", List.of());
    }

    private AccessPolicy getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "策略不存在"));
    }

    private AccessPolicyVO toVO(AccessPolicy e) {
        return new AccessPolicyVO(e.getId(), e.getName(), e.getDescription(), e.getConditionJson(),
                e.getEffect(), e.getPriority(), e.getTargetResource(), e.getTargetAction(), e.getStatus());
    }
}
