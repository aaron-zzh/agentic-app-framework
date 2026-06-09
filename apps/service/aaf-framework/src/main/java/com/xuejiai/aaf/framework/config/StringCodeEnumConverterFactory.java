package com.xuejiai.aaf.framework.config;

import java.lang.reflect.Method;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * 通用 String → Enum（带 code 字段）转换工厂。
 *
 * <p>对所有拥有 {@code getCode()} 方法且返回 String 的枚举生效， 支持 query string 按 code 转换（如 {@code
 * ?period=day}），不需要枚举实现特定接口。
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class StringCodeEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        // 检测该枚举是否有 String getCode() 方法
        Method getCode;
        try {
            getCode = targetType.getMethod("getCode");
            if (!String.class.equals(getCode.getReturnType())) return null;
        } catch (NoSuchMethodException e) {
            return null; // 无 getCode()，走 Spring 默认枚举名转换
        }

        final Method codeMethod = getCode;
        return source -> {
            for (T constant : targetType.getEnumConstants()) {
                try {
                    if (source.equalsIgnoreCase((String) codeMethod.invoke(constant))) {
                        return constant;
                    }
                } catch (Exception ignored) {
                }
            }
            throw new IllegalArgumentException(
                    "Unknown code '" + source + "' for " + targetType.getSimpleName());
        };
    }
}
