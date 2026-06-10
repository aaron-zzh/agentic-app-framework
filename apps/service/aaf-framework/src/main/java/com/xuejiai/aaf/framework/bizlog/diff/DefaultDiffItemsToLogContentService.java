package com.xuejiai.aaf.framework.bizlog.diff;

import java.lang.reflect.Field;
import java.util.*;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.xuejiai.aaf.framework.bizlog.annotation.DIffLogIgnore;
import com.xuejiai.aaf.framework.bizlog.annotation.DiffLogAllFields;
import com.xuejiai.aaf.framework.bizlog.annotation.DiffLogField;
import com.xuejiai.aaf.framework.bizlog.configuration.LogRecordProperties;
import com.xuejiai.aaf.framework.bizlog.service.IFunctionService;

import de.danielbechler.diff.node.DiffNode;
import de.danielbechler.diff.selector.ElementSelector;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * IDiffItemsToLogContentService 默认实现，将字段级 diff 转换为中文可读文案。
 *
 * <p>支持 @DiffLogField（自定义字段名）、@DiffLogAllFields（全量字段）、 @DIffLogIgnore（忽略字段）三个注解控制 diff 行为。
 */
@Slf4j
@Setter
@Getter
public class DefaultDiffItemsToLogContentService
        implements IDiffItemsToLogContentService, BeanFactoryAware, SmartInitializingSingleton {

    private IFunctionService functionService;
    private final LogRecordProperties logRecordProperties;
    private BeanFactory beanFactory;

    public DefaultDiffItemsToLogContentService(LogRecordProperties logRecordProperties) {
        this.logRecordProperties = logRecordProperties;
    }

    @Override
    public String toLogContent(DiffNode diffNode, Object sourceObject, Object targetObject) {
        if (!diffNode.hasChanges()) return "";
        DiffLogAllFields annotation = sourceObject.getClass().getAnnotation(DiffLogAllFields.class);
        var stringBuilder = new StringBuilder();
        Set<DiffNode> set = new HashSet<>();
        diffNode.visit(
                (node, visit) ->
                        generateAllFieldLog(
                                sourceObject, targetObject, stringBuilder, node, annotation, set));
        set.clear();
        return stringBuilder
                .toString()
                .replaceAll(logRecordProperties.getFieldSeparator() + "$", "");
    }

    private void generateAllFieldLog(
            Object sourceObject,
            Object targetObject,
            StringBuilder sb,
            DiffNode node,
            DiffLogAllFields annotation,
            Set<DiffNode> set) {
        if (node.isRootNode() || node.getValueTypeInfo() != null || set.contains(node)) return;
        DIffLogIgnore logIgnore = node.getFieldAnnotation(DIffLogIgnore.class);
        if (logIgnore != null) {
            memorandum(node, set);
            return;
        }
        DiffLogField diffLogFieldAnnotation = node.getFieldAnnotation(DiffLogField.class);
        if (annotation == null && diffLogFieldAnnotation == null) return;
        String filedLogName = getFieldLogName(node, diffLogFieldAnnotation, annotation != null);
        if (!StringUtils.hasText(filedLogName)) return;
        boolean valueIsContainer = valueIsContainer(node, sourceObject, targetObject);
        String functionName =
                diffLogFieldAnnotation != null ? diffLogFieldAnnotation.function() : "";
        String logContent =
                valueIsContainer
                        ? getCollectionDiffLogContent(
                                filedLogName, node, sourceObject, targetObject, functionName)
                        : getDiffLogContent(
                                filedLogName, node, sourceObject, targetObject, functionName);
        if (StringUtils.hasText(logContent)) {
            sb.append(logContent).append(logRecordProperties.getFieldSeparator());
        }
        memorandum(node, set);
    }

    @SuppressWarnings("unchecked")
    private void memorandum(DiffNode node, Set<DiffNode> set) {
        set.add(node);
        if (node.hasChildren()) {
            Field childrenField = ReflectionUtils.findField(DiffNode.class, "children");
            assert childrenField != null;
            ReflectionUtils.makeAccessible(childrenField);
            Map<ElementSelector, DiffNode> children =
                    (Map<ElementSelector, DiffNode>) ReflectionUtils.getField(childrenField, node);
            assert children != null;
            children.values().forEach(child -> memorandum(child, set));
        }
    }

    private String getFieldLogName(
            DiffNode node, DiffLogField diffLogFieldAnnotation, boolean isField) {
        String filedLogName =
                diffLogFieldAnnotation != null
                        ? diffLogFieldAnnotation.name()
                        : node.getPropertyName();
        if (node.getParentNode() != null) {
            filedLogName = getParentFieldName(node, isField) + filedLogName;
        }
        return filedLogName;
    }

    private boolean valueIsContainer(DiffNode node, Object sourceObject, Object targetObject) {
        if (sourceObject != null) {
            Object sourceValue = node.canonicalGet(sourceObject);
            if (sourceValue == null) {
                if (targetObject != null) {
                    Object tv = node.canonicalGet(targetObject);
                    return tv instanceof Collection || tv.getClass().isArray();
                }
            } else {
                return sourceValue instanceof Collection || sourceValue.getClass().isArray();
            }
        }
        return false;
    }

    private String getParentFieldName(DiffNode node, boolean isField) {
        DiffNode parent = node.getParentNode();
        String fieldNamePrefix = "";
        while (parent != null) {
            DiffLogField annotation = parent.getFieldAnnotation(DiffLogField.class);
            if ((annotation == null && !isField) || parent.isRootNode()) {
                parent = parent.getParentNode();
                continue;
            }
            fieldNamePrefix =
                    annotation != null
                            ? annotation.name() + logRecordProperties.getOfWord() + fieldNamePrefix
                            : parent.getPropertyName()
                                    + logRecordProperties.getOfWord()
                                    + fieldNamePrefix;
            parent = parent.getParentNode();
        }
        return fieldNamePrefix;
    }

    public String getCollectionDiffLogContent(
            String filedLogName,
            DiffNode node,
            Object sourceObject,
            Object targetObject,
            String functionName) {
        Collection<Object> sourceList = getListValue(node, sourceObject);
        Collection<Object> targetList = getListValue(node, targetObject);
        Collection<Object> addItemList = listSubtract(targetList, sourceList);
        Collection<Object> delItemList = listSubtract(sourceList, targetList);
        String listAddContent = listToContent(functionName, addItemList);
        String listDelContent = listToContent(functionName, delItemList);
        return logRecordProperties.formatList(filedLogName, listAddContent, listDelContent);
    }

    public String getDiffLogContent(
            String filedLogName,
            DiffNode node,
            Object sourceObject,
            Object targetObject,
            String functionName) {
        return switch (node.getState()) {
            case ADDED ->
                    logRecordProperties.formatAdd(
                            filedLogName,
                            getFunctionValue(node.canonicalGet(targetObject), functionName));
            case CHANGED ->
                    logRecordProperties.formatUpdate(
                            filedLogName,
                            getFunctionValue(node.canonicalGet(sourceObject), functionName),
                            getFunctionValue(node.canonicalGet(targetObject), functionName));
            case REMOVED ->
                    logRecordProperties.formatDeleted(
                            filedLogName,
                            getFunctionValue(node.canonicalGet(sourceObject), functionName));
            default -> {
                log.warn("diff log not support state: {}", node.getState());
                yield "";
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Collection<Object> getListValue(DiffNode node, Object object) {
        Object fieldSourceValue = node.canonicalGet(object);
        if (fieldSourceValue != null && fieldSourceValue.getClass().isArray()) {
            return new ArrayList<>(Arrays.asList((Object[]) fieldSourceValue));
        }
        return fieldSourceValue == null ? new ArrayList<>() : (Collection<Object>) fieldSourceValue;
    }

    private Collection<Object> listSubtract(
            Collection<Object> minuend, Collection<Object> subTractor) {
        Collection<Object> result = new ArrayList<>(minuend);
        result.removeAll(subTractor);
        return result;
    }

    private String listToContent(String functionName, Collection<Object> items) {
        if (CollectionUtils.isEmpty(items)) return "";
        var sb = new StringBuilder();
        items.forEach(
                item ->
                        sb.append(getFunctionValue(item, functionName))
                                .append(logRecordProperties.getListItemSeparator()));
        return sb.toString().replaceAll(logRecordProperties.getListItemSeparator() + "$", "");
    }

    private String getFunctionValue(Object value, String functionName) {
        if (!StringUtils.hasText(functionName)) {
            return value == null ? "" : value.toString();
        }
        return functionService.apply(functionName, value == null ? "" : value.toString());
    }

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.functionService = beanFactory.getBean(IFunctionService.class);
    }
}
