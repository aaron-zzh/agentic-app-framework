package com.xuejiai.aaf.framework.bizlog.service.impl;

import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.aop.support.AopUtils;

import com.xuejiai.aaf.framework.bizlog.context.LogRecordContext;
import com.xuejiai.aaf.framework.bizlog.diff.IDiffItemsToLogContentService;

import de.danielbechler.diff.ObjectDifferBuilder;
import de.danielbechler.diff.comparison.ComparisonService;
import de.danielbechler.diff.node.DiffNode;
import lombok.extern.slf4j.Slf4j;

/**
 * 内置 diff 解析函数，在 @LogRecord 模板中使用 {@code {_DIFF{#newObj}}} 或 {@code {_DIFF{#oldObj, #newObj}}}
 * 自动生成字段级变更描述。
 */
@Slf4j
public class DiffParseFunction {

    /** 模板中 diff 函数的固定名称。 */
    public static final String diffFunctionName = "_DIFF";

    /** LogRecordContext 中存放旧对象的键名。 */
    public static final String OLD_OBJECT = "_oldObj";

    private IDiffItemsToLogContentService diffItemsToLogContentService;

    /** 额外使用 equals 方法比较的类型集合（默认 java-object-diff 用反射比较字段）。 */
    private final Set<Class<?>> comparisonSet = new HashSet<>();

    /**
     * 比较两个对象，返回可读 diff 文案。
     *
     * @param source 旧对象
     * @param target 新对象
     */
    public String diff(Object source, Object target) {
        if (source == null && target == null) return "";
        if (source == null || target == null) {
            try {
                Class<?> clazz = source == null ? target.getClass() : source.getClass();
                source = source == null ? clazz.getDeclaredConstructor().newInstance() : source;
                target = target == null ? clazz.getDeclaredConstructor().newInstance() : target;
            } catch (InstantiationException
                    | IllegalAccessException
                    | NoSuchMethodException
                    | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
        if (!Objects.equals(
                AopUtils.getTargetClass(source.getClass()),
                AopUtils.getTargetClass(target.getClass()))) {
            log.error("diff 两个对象类型不同：source={}, target={}", source.getClass(), target.getClass());
            return "";
        }
        var builder = ObjectDifferBuilder.startBuilding();
        var register =
                builder.differs()
                        .register(
                                (differDispatcher, nodeQueryService) ->
                                        new com.xuejiai.aaf.framework.bizlog.util.diff.ArrayDiffer(
                                                differDispatcher,
                                                (ComparisonService) builder.comparison(),
                                                builder.identity()));
        comparisonSet.forEach(clazz -> register.comparison().ofType(clazz).toUseEqualsMethod());
        DiffNode diffNode = register.build().compare(target, source);
        return diffItemsToLogContentService.toLogContent(diffNode, source, target);
    }

    /** 单参数 diff：从 LogRecordContext 中取旧对象（键名 {@link #OLD_OBJECT}）与新对象比较。 */
    public String diff(Object newObj) {
        Object oldObj = LogRecordContext.getMethodOrGlobal(OLD_OBJECT);
        return diff(oldObj, newObj);
    }

    public void setDiffItemsToLogContentService(IDiffItemsToLogContentService service) {
        this.diffItemsToLogContentService = service;
    }

    public void addUseEqualsClass(List<String> classList) {
        if (classList == null) return;
        for (String clazz : classList) {
            try {
                comparisonSet.add(Class.forName(clazz));
            } catch (ClassNotFoundException e) {
                log.warn("无效的比对类型：className={}", clazz);
            }
        }
    }

    public void addUseEqualsClass(Class<?> clazz) {
        comparisonSet.add(clazz);
    }
}
