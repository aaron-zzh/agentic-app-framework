package com.xuejiai.aaf.module.tool.medical;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * 医疗问答工具注册——将 MedicalAnswerTool 包装为 ToolCallback 并补充元数据；工具始终注册，未配置 api-key 时调用才报错。
 *
 * <p>风险等级为 MEDIUM：解答是第三方大模型生成的医疗内容，且问题会发送到第三方服务。
 */
@Configuration
@RequiredArgsConstructor
public class MedicalAnswerToolConfiguration {

    private final MedicalAnswerTool medicalAnswerTool;
    private final ToolRegistry toolRegistry;

    @Bean
    public ToolCallbackProvider medicalAnswerToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(medicalAnswerTool).build();
    }

    @PostConstruct
    void registerMeta() {
        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        MedicalAnswerTool.TOOL_NAME,
                        "回答健康知识问题并给出依据来源（阿里云 IQS 医疗问答，仅供参考）",
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.HTTP,
                        ToolRiskLevel.MEDIUM,
                        true,
                        """
                {"type":"object","required":["query"],
                 "properties":{
                   "query":{"type":"string","description":"通用的健康问题，不得包含个人隐私信息"}
                 }}"""));
    }
}
