package com.xuejiai.aaf.config;

import java.nio.file.Path;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.xuejiai.aaf.framework.storage.StorageProperties;

import lombok.RequiredArgsConstructor;

/**
 * 本地存储静态资源映射。
 *
 * <p>将本地文件目录映射为 HTTP 可访问路径。
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aaf.storage", name = "type", havingValue = "local")
public class StorageWebConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        var local = properties.local();
        // urlPrefix 可能是完整 URL（如 http://localhost:8080/files），提取路径部分
        String urlPrefix = local.urlPrefix();
        String path =
                urlPrefix.startsWith("http") ? java.net.URI.create(urlPrefix).getPath() : urlPrefix;
        // 用 Path.toUri() 生成跨平台标准 file URI（Windows 下避免反斜杠混入 "file:" 后导致解析异常）
        String location = Path.of(local.basePath()).toAbsolutePath().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(path + "/**").addResourceLocations(location);
    }
}
