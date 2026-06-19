package com.xuejiai.aaf.framework.engine.tool.builtin;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** OCR 工具注册——将 OcrTool 包装为 ToolCallback 并补充元数据。 */
@Configuration
@RequiredArgsConstructor
public class OcrToolConfiguration {

    private final OcrTool ocrTool;
    private final ToolRegistry toolRegistry;

    /** 将 OcrTool 的 @Tool 方法包装为 ToolCallback Bean，供 ToolRegistry 自动收集。 */
    @Bean
    public ToolCallbackProvider ocrToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(ocrTool).build();
    }

    /** 补充元数据（风险等级、readOnly 等 ToolCallback 默认值不包含的信息）。 */
    @PostConstruct
    void registerMeta() {
        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        "recognizeOcr",
                        "从图像中提取文字或结构化信息（OCR）",
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.FUNCTION,
                        ToolRiskLevel.LOW,
                        true,
                        """
                {"type":"object","properties":{
                  "requestJson":{"type":"string","description":"OCR 识别 JSON 参数，包含 imageUrl（必填）、task（可选）、prompt（可选）、modelId（可选）"}
                },"required":["requestJson"]}"""));
    }
}
