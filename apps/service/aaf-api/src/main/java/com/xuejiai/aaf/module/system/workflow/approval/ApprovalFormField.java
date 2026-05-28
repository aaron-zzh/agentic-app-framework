package com.xuejiai.aaf.module.system.workflow.approval;

import java.util.List;

/**
 * 审批表单字段定义。
 *
 * @param name 字段名
 * @param label 显示标签
 * @param type 字段类型
 * @param required 是否必填
 * @param options 选项列表（SELECT 类型时使用）
 * @param defaultValue 默认值
 */
public record ApprovalFormField(
        String name,
        String label,
        FieldType type,
        boolean required,
        List<String> options,
        String defaultValue) {

    /** 表单字段类型 */
    public enum FieldType {
        TEXT, NUMBER, DATE, SELECT, TEXTAREA, FILE
    }
}
