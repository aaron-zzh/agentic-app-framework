package com.xuejiai.aaf.framework.security.license;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 插件注册表，按授权状态过滤高级插件。 */
@Component
public class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final List<Plugin> registered = new ArrayList<>();

    /** 注册插件列表，premium=false 时跳过高级插件。 */
    public void register(List<Plugin> plugins) {
        for (Plugin plugin : plugins) {
            if (plugin.requiresPremium() && !License.get().isPremium()) {
                log.debug("跳过高级插件：{}", plugin.getName());
                continue;
            }
            plugin.initialize();
            registered.add(plugin);
        }
    }

    public List<Plugin> getRegistered() {
        return Collections.unmodifiableList(registered);
    }
}
