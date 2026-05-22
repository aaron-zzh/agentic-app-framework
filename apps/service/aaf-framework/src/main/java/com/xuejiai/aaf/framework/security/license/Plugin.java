package com.xuejiai.aaf.framework.security.license;

/** 插件接口，框架插件实现此接口以支持授权过滤。 */
public interface Plugin {
    String getName();

    boolean requiresPremium();

    void initialize();
}
