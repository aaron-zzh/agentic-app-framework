package com.xuejiai.aaf.module.elder.agent.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;
import com.xuejiai.aaf.module.elder.agent.tool.ElderAssistantTool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** 养老助理工具注册与运行时元数据。 */
@Configuration
@RequiredArgsConstructor
public class ElderToolConfiguration {

    private final ElderAssistantTool elderAssistantTool;
    private final ToolRegistry toolRegistry;

    @Bean
    public ToolCallbackProvider elderToolCallbackProvider() {
        return MethodToolCallbackProvider.builder().toolObjects(elderAssistantTool).build();
    }

    @PostConstruct
    void registerMeta() {
        register("queryElderOverview", "查询当前长者脱敏概况", ToolRiskLevel.LOW, true);
        register("listAvailableServices", "列出可预约养老服务", ToolRiskLevel.LOW, true);
        register("draftServiceOrder", "起草十分钟服务预约草稿", ToolRiskLevel.LOW, false);
        register("confirmServiceOrder", "确认草稿并创建待运营确认预约", ToolRiskLevel.HIGH, false);
        register("queryServiceOrderStatus", "查询最近服务预约状态", ToolRiskLevel.LOW, true);
        register("queryAlertStatus", "查询最近预警进展", ToolRiskLevel.LOW, true);
        register("queryDeviceStatus", "查询设备在线状态", ToolRiskLevel.LOW, true);
        register("listMedicationPlans", "查询用药计划与今日服药事实", ToolRiskLevel.LOW, true);
        register("draftMedicationPlan", "起草十分钟用药计划草稿", ToolRiskLevel.LOW, false);
        register("confirmMedicationPlan", "确认草稿并创建对话来源用药计划", ToolRiskLevel.HIGH, false);
        register("listRecentPhotos", "查询最近长者照片", ToolRiskLevel.LOW, true);
        register("searchRehabGuide", "检索已发布康复指导并返回引用", ToolRiskLevel.MEDIUM, true);
    }

    private void register(
            String name, String description, ToolRiskLevel riskLevel, boolean readOnly) {
        toolRegistry.registerMeta(
                new ToolRegistry.ToolMeta(
                        name,
                        description,
                        ToolRegistry.SOURCE_LOCAL,
                        ToolType.FUNCTION,
                        riskLevel,
                        readOnly,
                        "{\"type\":\"object\"}"));
    }
}
