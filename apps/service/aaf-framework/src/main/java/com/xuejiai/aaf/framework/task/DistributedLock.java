package com.xuejiai.aaf.framework.task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式分布式锁注解。
 *
 * <p>标注在方法上，执行前自动获取 Redis 锁，执行后释放。获取失败则跳过执行。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /** 锁的 key，支持 SpEL 表达式 */
    String key();

    /** 锁超时时间（秒），默认 5 分钟 */
    long ttlSeconds() default 300;
}
