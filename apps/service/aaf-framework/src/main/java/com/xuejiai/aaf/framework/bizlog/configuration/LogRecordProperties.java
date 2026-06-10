package com.xuejiai.aaf.framework.bizlog.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import lombok.Data;

/**
 * 操作日志配置属性，前缀 {@code aaf.log.record}。
 *
 * <p>支持自定义 diff 日志的文案模板、分隔符等。
 */
@ConfigurationProperties(prefix = "aaf.log.record")
@Data
public class LogRecordProperties {

    private static final String FIELD_PLACEHOLDER = "__fieldName";
    private static final String SOURCE_VALUE_PLACEHOLDER = "__sourceValue";
    private static final String TARGET_VALUE_PLACEHOLDER = "__targetValue";
    private static final String LIST_ADD_VALUE_PLACEHOLDER = "__addValues";
    private static final String LIST_DEL_VALUE_PLACEHOLDER = "__delValues";

    /** 字段从空改为有值时的日志模板。 */
    private String addTemplate =
            "【" + FIELD_PLACEHOLDER + "】从【空】修改为【" + TARGET_VALUE_PLACEHOLDER + "】";

    /** 列表仅有新增项时的模板。 */
    private String addTemplateForList =
            "【" + FIELD_PLACEHOLDER + "】添加了【" + LIST_ADD_VALUE_PLACEHOLDER + "】";

    /** 列表仅有删除项时的模板。 */
    private String deleteTemplateForList =
            "【" + FIELD_PLACEHOLDER + "】删除了【" + LIST_DEL_VALUE_PLACEHOLDER + "】";

    /** 列表既有新增又有删除时的模板。 */
    private String updateTemplateForList =
            "【"
                    + FIELD_PLACEHOLDER
                    + "】添加了【"
                    + LIST_ADD_VALUE_PLACEHOLDER
                    + "】删除了【"
                    + LIST_DEL_VALUE_PLACEHOLDER
                    + "】";

    /** 字段更新时的日志模板。 */
    private String updateTemplate =
            "【"
                    + FIELD_PLACEHOLDER
                    + "】从【"
                    + SOURCE_VALUE_PLACEHOLDER
                    + "】修改为【"
                    + TARGET_VALUE_PLACEHOLDER
                    + "】";

    /** 字段值被清空时的日志模板。 */
    private String deleteTemplate =
            "删除了【" + FIELD_PLACEHOLDER + "】：【" + SOURCE_VALUE_PLACEHOLDER + "】";

    /** 多字段日志拼接分隔符。 */
    private String fieldSeparator = "；";

    /** 列表项之间的分隔符。 */
    private String listItemSeparator = "，";

    /** 嵌套对象字段名称连接词，如"创建人『的』用户ID"。 */
    private String ofWord = "的";

    /** 为 true 时，diff 无变化也记录日志；为 false 时（默认）无变化不记录。 */
    private Boolean diffLog = false;

    /** 需要使用 equals 方法比较的类名列表（逗号分隔）。 */
    private String useEqualsMethod;

    public String formatAdd(String fieldName, Object targetValue) {
        return addTemplate
                .replace(FIELD_PLACEHOLDER, fieldName)
                .replace(TARGET_VALUE_PLACEHOLDER, String.valueOf(targetValue));
    }

    public String formatUpdate(String fieldName, Object sourceValue, Object targetValue) {
        return updateTemplate
                .replace(FIELD_PLACEHOLDER, fieldName)
                .replace(SOURCE_VALUE_PLACEHOLDER, String.valueOf(sourceValue))
                .replace(TARGET_VALUE_PLACEHOLDER, String.valueOf(targetValue));
    }

    public String formatDeleted(String fieldName, Object sourceValue) {
        return deleteTemplate
                .replace(FIELD_PLACEHOLDER, fieldName)
                .replace(SOURCE_VALUE_PLACEHOLDER, String.valueOf(sourceValue));
    }

    public String formatList(String fieldName, String addContent, String delContent) {
        boolean hasAdd = StringUtils.hasText(addContent);
        boolean hasDel = StringUtils.hasText(delContent);
        if (hasAdd && !hasDel) {
            return addTemplateForList
                    .replace(FIELD_PLACEHOLDER, fieldName)
                    .replace(LIST_ADD_VALUE_PLACEHOLDER, addContent);
        }
        if (!hasAdd && hasDel) {
            return deleteTemplateForList
                    .replace(FIELD_PLACEHOLDER, fieldName)
                    .replace(LIST_DEL_VALUE_PLACEHOLDER, delContent);
        }
        if (hasAdd) {
            return updateTemplateForList
                    .replace(FIELD_PLACEHOLDER, fieldName)
                    .replace(LIST_ADD_VALUE_PLACEHOLDER, addContent)
                    .replace(LIST_DEL_VALUE_PLACEHOLDER, delContent);
        }
        return "";
    }
}
