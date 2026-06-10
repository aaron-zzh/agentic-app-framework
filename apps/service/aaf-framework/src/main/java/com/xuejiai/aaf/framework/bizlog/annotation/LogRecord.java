package com.xuejiai.aaf.framework.bizlog.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解，标注在 Service/Controller 方法上，AOP 切面自动记录操作日志。
 *
 * <p>模板中使用 {@code {{#参数名}}} 引用方法参数，使用 {@code {函数名{#参数}}} 将参数值转换为可读文案（需注册 IParseFunction）， 使用 {@code
 * {_DIFF{#newObj}}} 自动生成字段级变更描述。
 *
 * <p>示例：
 *
 * <pre>{@code
 * @LogRecord(type = "用户", bizNo = "{{#userId}}", success = "修改了用户 {getUserName{#userId}} 的角色")
 * public void updateRole(Long userId, String role) { ... }
 * }</pre>
 */
@Repeatable(LogRecords.class)
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface LogRecord {

    /** 方法执行成功后的日志模板（支持 SpEL 和自定义函数）。 */
    String success();

    /** 方法执行失败后的日志模板（可引用 {@code _errorMsg} 内置变量）。 */
    String fail() default "";

    /** 操作人（SpEL 表达式），为空时从 IOperatorGetService 自动获取。 */
    String operator() default "";

    /** 日志类型，如"订单"、"用户"。 */
    String type();

    /** 日志子类型，用于区分同类型下不同场景的日志。 */
    String subType() default "";

    /** 业务标识（SpEL 表达式），如订单号、用户 ID。 */
    String bizNo();

    /** 额外扩展信息（SpEL 表达式，通常为 JSON 字符串）。 */
    String extra() default "";

    /** 记录日志的条件（SpEL 表达式），结果为 false 时不记录。 */
    String condition() default "";

    /**
     * 成功条件（SpEL 表达式）。 非空时：结果为 true 走 success 模板，结果为 false 走 fail 模板。 为空时：无异常为成功走 success，有异常走 fail。
     */
    String successCondition() default "";
}
