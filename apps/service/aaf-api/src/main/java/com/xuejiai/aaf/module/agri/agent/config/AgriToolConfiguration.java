package com.xuejiai.aaf.module.agri.agent.config;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xuejiai.aaf.framework.engine.tool.ToolRegistry;
import com.xuejiai.aaf.framework.engine.tool.ToolRiskLevel;
import com.xuejiai.aaf.framework.engine.tool.ToolType;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDemoTool;
import com.xuejiai.aaf.module.agri.agent.tool.AgriDeviceDemoTool;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/** 智慧农业演示工具（含物联网设备演示工具）注册与运行时元数据。 */
@Configuration
@RequiredArgsConstructor
public class AgriToolConfiguration {

    private final AgriDemoTool agriDemoTool;
    private final AgriDeviceDemoTool agriDeviceDemoTool;
    private final ToolRegistry toolRegistry;

    @Bean
    public ToolCallbackProvider agriToolCallbackProvider() {
        return MethodToolCallbackProvider.builder()
                .toolObjects(agriDemoTool, agriDeviceDemoTool)
                .build();
    }

    @PostConstruct
    void registerMeta() {
        register("agriSensorTrend", "查询大棚环境指标趋势（示例数据）", ToolRiskLevel.LOW, true);
        register("agriSensorAlerts", "列出大棚环境异常（示例数据）", ToolRiskLevel.LOW, true);
        register("agriSalesSummary", "汇总农产品销量、销售额与库存（示例数据）", ToolRiskLevel.LOW, true);
        register("agriBookableProducts", "列出可预约农产品与时段（示例数据）", ToolRiskLevel.LOW, true);
        register("agriDraftBooking", "起草十分钟采摘或配送预约草稿", ToolRiskLevel.LOW, true);
        register("agriConfirmBooking", "确认草稿并创建示例预约", ToolRiskLevel.HIGH, false);
        register("agriSensorRealtime", "查询大棚环境实时读数（示例数据）", ToolRiskLevel.LOW, true);
        register("agriDeviceList", "列出大棚物联网设备与状态（示例数据）", ToolRiskLevel.LOW, true);
        register("agriDeviceStatus", "查询单台设备状态与操作记录（示例数据）", ToolRiskLevel.LOW, true);
        register("agriDraftDeviceCommand", "起草十分钟设备控制草稿", ToolRiskLevel.LOW, true);
        register("agriConfirmDeviceCommand", "确认草稿并执行示例设备控制", ToolRiskLevel.HIGH, false);
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
