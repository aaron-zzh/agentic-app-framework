package com.xuejiai.aaf.framework.security.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PluginRegistryTest {

    private PluginRegistry registry;

    @BeforeEach
    void setUp() {
        License.get().reset();
        registry = new PluginRegistry();
    }

    @AfterEach
    void tearDown() {
        License.get().reset();
    }

    @Test
    @DisplayName("Given premium=true When 注册含高级插件 Then 所有插件注册成功")
    void should_register_all_when_premium() {
        // 准备参数
        License.get().activate("user-1", "pro", Instant.now().plusSeconds(3600));
        var freePlugin = mockPlugin("free-plugin", false);
        var premiumPlugin = mockPlugin("premium-plugin", true);

        // 调用
        registry.register(List.of(freePlugin, premiumPlugin));

        // 断言
        assertThat(registry.getRegistered()).hasSize(2);
        verify(freePlugin).initialize();
        verify(premiumPlugin).initialize();
    }

    @Test
    @DisplayName("Given premium=false When 注册含高级插件 Then 高级插件被跳过")
    void should_skip_premium_plugins_when_free() {
        // 准备参数
        var freePlugin = mockPlugin("free-plugin", false);
        var premiumPlugin = mockPlugin("premium-plugin", true);

        // 调用
        registry.register(List.of(freePlugin, premiumPlugin));

        // 断言
        assertThat(registry.getRegistered()).hasSize(1);
        assertThat(registry.getRegistered().get(0).getName()).isEqualTo("free-plugin");
        verify(freePlugin).initialize();
        verify(premiumPlugin, never()).initialize();
    }

    // ── 辅助方法 ──────────────────────────────────────────────

    private Plugin mockPlugin(String name, boolean requiresPremium) {
        var plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getName()).thenReturn(name);
        Mockito.when(plugin.requiresPremium()).thenReturn(requiresPremium);
        return plugin;
    }
}
