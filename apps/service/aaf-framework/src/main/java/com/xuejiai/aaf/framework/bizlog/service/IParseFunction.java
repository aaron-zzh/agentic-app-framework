package com.xuejiai.aaf.framework.bizlog.service;

/**
 * 自定义解析函数接口。
 *
 * <p>实现此接口并注册为 Spring Bean，即可在 @LogRecord 模板中使用 {@code {函数名{#参数}}} 语法将 ID 等原始值转换为可读文案。
 *
 * <p>示例：注册函数名为 "getUserName"，模板中写 {@code {getUserName{#userId}}} 将用户 ID 转为昵称。
 */
public interface IParseFunction {

    /**
     * 是否在方法执行前调用此函数。
     *
     * <p>更新场景需要拿到修改前的旧值时，应返回 true， 此时函数内不能使用 {@code _ret} 和 {@code _errorMsg} 内置变量。
     */
    default boolean executeBefore() {
        return false;
    }

    /** 函数名称，与模板中的函数名一一对应。 */
    String functionName();

    /**
     * 将原始值转换为可读文案。
     *
     * @param value SpEL 表达式解析出的参数值
     * @return 可读文案，如 "张三(13800138000)"
     */
    String apply(Object value);
}
