package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 审批表单模板服务——管理表单模板的 CRUD 和表单数据生成。
 *
 * @author AaronZZH
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalFormService {

    private final ApprovalFormTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    /** 创建表单模板。 */
    @Transactional
    public ApprovalFormTemplate createTemplate(
            String name, String description, String processKey, List<ApprovalFormField> fields) {
        var template = new ApprovalFormTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setProcessKey(processKey);
        template.setFieldsJson(toJson(fields));
        template.setStatus(1);
        return templateRepository.save(template);
    }

    /** 更新表单模板。 */
    @Transactional
    public ApprovalFormTemplate updateTemplate(
            Long id, String name, String description, List<ApprovalFormField> fields) {
        var template =
                templateRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new BusinessException(GlobalErrorCode.NOT_FOUND, "表单模板不存在"));
        template.setName(name);
        template.setDescription(description);
        template.setFieldsJson(toJson(fields));
        return templateRepository.save(template);
    }

    /** 按流程定义 Key 查询启用的模板。 */
    @Transactional(readOnly = true)
    public Optional<ApprovalFormTemplate> getByProcessKey(String processKey) {
        return templateRepository.findByProcessKeyAndStatus(processKey, 1);
    }

    /** 查询所有模板。 */
    @Transactional(readOnly = true)
    public List<ApprovalFormTemplate> listTemplates() {
        return templateRepository.findAll();
    }

    /** 解析模板字段定义。 */
    public List<ApprovalFormField> parseFields(ApprovalFormTemplate template) {
        try {
            return objectMapper.readValue(
                    template.getFieldsJson(), new TypeReference<List<ApprovalFormField>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "表单字段解析失败");
        }
    }

    private String toJson(List<ApprovalFormField> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR, "表单字段序列化失败");
        }
    }
}
