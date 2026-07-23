package com.xuejiai.aaf.framework.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 *
 * <p>标记在 Controller/Service 方法上，AOP 切面自动记录操作日志。description 和 bizNo 仅支持以 #{} 包裹的安全变量属性路径。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /** 模块名 */
    String module();

    /** 操作类型 */
    OperationType type();

    /** 操作描述，支持安全路径，如 "删除用户 #{dto.username}"。 */
    String description() default "";

    /** 业务编号安全路径模板（可选）。 */
    String bizNo() default "";
}
