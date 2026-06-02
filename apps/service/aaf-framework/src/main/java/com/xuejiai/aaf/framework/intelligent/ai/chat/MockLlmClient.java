package com.xuejiai.aaf.framework.intelligent.ai.chat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.core.llm.LlmClient;

import reactor.core.publisher.Flux;

/**
 * Mock LLM 客户端——测试/评估时使用，不调用真实 API。
 *
 * <p>支持两种模式：
 *
 * <ul>
 *   <li>预设响应：通过 {@link #preset(String, String)} 注册关键词→响应映射
 *   <li>自定义生成器：通过 {@link #setGenerator(Function)} 注入响应生成逻辑
 * </ul>
 *
 * <p>激活方式：{@code aaf.llm.mock=true}
 */
@Component
@ConditionalOnProperty(name = "aaf.llm.mock", havingValue = "true")
public class MockLlmClient implements LlmClient {

    private final Map<String, String> presets = new ConcurrentHashMap<>();
    private Function<List<LlmMessage>, String> generator = this::defaultGenerate;

    /** 注册预设响应：用户消息包含 keyword 时返回 response */
    public void preset(String keyword, String response) {
        presets.put(keyword, response);
    }

    /** 设置自定义响应生成器 */
    public void setGenerator(Function<List<LlmMessage>, String> generator) {
        this.generator = generator;
    }

    /** 清空预设 */
    public void reset() {
        presets.clear();
        generator = this::defaultGenerate;
    }

    @Override
    public String call(List<LlmMessage> messages, String scene, Long userId) {
        return generator.apply(messages);
    }

    @Override
    public Flux<String> stream(List<LlmMessage> messages, String scene, Long userId) {
        var result = call(messages, scene, userId);
        // 模拟流式：按句号分割
        var parts = result.split("(?<=。|\\.|\\n)");
        return Flux.fromArray(parts);
    }

    private String defaultGenerate(List<LlmMessage> messages) {
        // 查找最后一条用户消息
        var lastUser =
                messages.stream()
                        .filter(m -> "user".equals(m.role()))
                        .reduce((a, b) -> b)
                        .map(LlmMessage::content)
                        .orElse("");

        // 匹配预设
        for (var entry : presets.entrySet()) {
            if (lastUser.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 默认回显
        return "[Mock] 收到: " + lastUser.substring(0, Math.min(lastUser.length(), 100));
    }
}
