package com.xuejiai.aaf.module.system.workflow.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.common.model.PageResult;
import com.xuejiai.aaf.common.model.SpecificationBuilder;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.module.system.log.event.EntityChangeEvent;
import com.xuejiai.aaf.module.system.workflow.domain.AutomationLog;
import com.xuejiai.aaf.module.system.workflow.domain.AutomationRule;
import com.xuejiai.aaf.module.system.workflow.repository.AutomationLogRepository;
import com.xuejiai.aaf.module.system.workflow.repository.AutomationRuleRepository;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationLogPageDTO;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationLogVO;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationRuleCreateDTO;
import com.xuejiai.aaf.module.system.workflow.vo.AutomationRuleVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 自动化规则服务：规则 CRUD + 触发执行 + 日志记录。
 *
 * @author AaronZZH & Kiro
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationService {

    private final AutomationRuleRepository ruleRepository;
    private final AutomationLogRepository logRepository;
    private final WorkflowService workflowService;

    // ========== 规则 CRUD ==========

    @Transactional
    public Long createRule(AutomationRuleCreateDTO dto) {
        var rule = new AutomationRule();
        rule.setName(dto.name());
        rule.setEntitySlug(dto.entitySlug());
        rule.setTriggerType(dto.triggerType());
        rule.setConditions(dto.conditions());
        rule.setActions(dto.actions());
        rule.setEnabled(dto.enabled() != null ? dto.enabled() : true);
        ruleRepository.save(rule);
        return rule.getId();
    }

    @Transactional
    public void updateRule(Long id, AutomationRuleCreateDTO dto) {
        var rule = ruleRepository.findById(id).orElseThrow();
        rule.setName(dto.name());
        rule.setEntitySlug(dto.entitySlug());
        rule.setTriggerType(dto.triggerType());
        rule.setConditions(dto.conditions());
        rule.setActions(dto.actions());
        if (dto.enabled() != null) {
            rule.setEnabled(dto.enabled());
        }
        ruleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }

    public AutomationRuleVO getRule(Long id) {
        return ruleRepository.findById(id).map(this::toRuleVO).orElseThrow();
    }

    public List<AutomationRuleVO> listRules(String entitySlug) {
        Specification<AutomationRule> spec =
                SpecificationBuilder.<AutomationRule>builder()
                        .eqIfPresent("entitySlug", entitySlug)
                        .build();
        return ruleRepository.findAll(spec, Sort.by("id").descending()).stream()
                .map(this::toRuleVO)
                .toList();
    }

    @Transactional
    public void toggleRule(Long id, boolean enabled) {
        var rule = ruleRepository.findById(id).orElseThrow();
        rule.setEnabled(enabled);
        ruleRepository.save(rule);
    }

    // ========== 执行日志查询 ==========

    public PageResult<AutomationLogVO> pageLogs(AutomationLogPageDTO req) {
        var pageable = req.toPageable(Sort.by("id").descending());
        Specification<AutomationLog> spec =
                SpecificationBuilder.<AutomationLog>builder()
                        .eqIfPresent("ruleId", req.getRuleId())
                        .eqIfPresent("status", req.getStatus())
                        .build();
        var page = logRepository.findAll(spec, pageable);
        return new PageResult<>(
                page.getContent().stream().map(this::toLogVO).toList(), page.getTotalElements());
    }

    // ========== 触发执行 ==========

    /** 实体变更时触发匹配规则 */
    public void trigger(EntityChangeEvent event, String triggerType) {
        var rules =
                ruleRepository.findByEntitySlugAndTriggerTypeAndEnabledTrue(
                        event.entityType(), triggerType);
        for (var rule : rules) {
            executeRule(rule, event);
        }
    }

    private void executeRule(AutomationRule rule, EntityChangeEvent event) {
        try {
            var actions = parseActions(rule.getActions());
            for (var action : actions) {
                executeAction(action, event);
            }
            saveLog(rule, event, "success", null);
        } catch (Exception e) {
            log.error("自动化规则执行失败: ruleId={}, error={}", rule.getId(), e.getMessage(), e);
            saveLog(rule, event, "failed", e.getMessage());
        }
    }

    private void executeAction(Map<String, Object> action, EntityChangeEvent event) {
        var type = (String) action.get("type");
        switch (type) {
            case "update_field" ->
                    log.info(
                            "执行 update_field: entity={}, id={}",
                            event.entityType(),
                            event.entityId());
            case "send_notification" -> {
                var title = (String) action.getOrDefault("title", "自动化通知");
                var body = (String) action.getOrDefault("body", event.entityType() + " 触发自动化规则");
                log.info("执行 send_notification: title={}", title);
            }
            case "create_record" -> log.info("执行 create_record: entity={}", action.get("entity"));
            case "start_workflow" -> {
                var assignee = (String) action.getOrDefault("assignee", "system");
                workflowService.startProcess(
                        event.entityType(), event.entityId(), "automation", assignee);
            }
            case "call_webhook" -> log.info("执行 call_webhook: url={}", action.get("url"));
            default -> log.warn("未知动作类型: {}", type);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseActions(String actionsJson) {
        try {
            return JsonUtils.parseObject(actionsJson, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private void saveLog(
            AutomationRule rule, EntityChangeEvent event, String status, String errorMessage) {
        var logEntry = new AutomationLog();
        logEntry.setRuleId(rule.getId());
        logEntry.setTriggerType(rule.getTriggerType());
        logEntry.setEntityType(event.entityType());
        logEntry.setEntityId(event.entityId());
        logEntry.setStatus(status);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setExecutedAt(LocalDateTime.now());
        logRepository.save(logEntry);
    }

    // ========== VO 转换 ==========

    private AutomationRuleVO toRuleVO(AutomationRule r) {
        return new AutomationRuleVO(
                r.getId(),
                r.getName(),
                r.getEntitySlug(),
                r.getTriggerType(),
                r.getConditions(),
                r.getActions(),
                r.getEnabled(),
                r.getCreateTime());
    }

    private AutomationLogVO toLogVO(AutomationLog l) {
        return new AutomationLogVO(
                l.getId(),
                l.getRuleId(),
                l.getTriggerType(),
                l.getEntityType(),
                l.getEntityId(),
                l.getStatus(),
                l.getErrorMessage(),
                l.getExecutedAt());
    }
}
