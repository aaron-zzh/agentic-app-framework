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

/** 医疗知识查询工具注册——将 MedicalKnowTool 包装为 ToolCallback 并补充元数据；工具始终注册，未配置 api-key 时调用才报错。 */
@Configuration
@RequiredArgsConstructor
public class MedicalKnowToolConfiguration {

    private final MedicalKnowTool medicalKnowTool;
    private final ToolRegistry toolRegistry;

    @Bean
    public ToolCallbackProvider medicalKnowToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(medicalKnowTool).build();
    }

    @PostConstruct
    void registerMeta() {
        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        MedicalKnowTool.TOOL_NAME,
                        "查询药品、病症、食品的权威医疗属性（阿里云 IQS 健康图谱）",
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.HTTP,
                        ToolRiskLevel.LOW,
                        true,
                        """
                {"type":"object","required":["query"],
                 "properties":{
                   "query":{"type":"string","description":"医疗实体名称，不得包含个人隐私信息"},
                   "type":{"type":"string","enum":["medicine","finding","food"],
                           "description":"实体类型，不确定可不传"}
                 }}"""));
    }
}
