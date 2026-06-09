package com.xuejiai.aaf.framework.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Web MVC 全局配置——注册枚举 Converter 等。 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        // 对所有实现 StringCodeEnum 的枚举，支持 query string 按 code 转换
        registry.addConverterFactory(new StringCodeEnumConverterFactory());
    }
}
