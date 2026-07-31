package com.xuejiai.aaf.framework.engine.dataprocess;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据路由步骤——按配置将处理后的数据写入目标存储。
 *
 * <p>知识库持久化尚未接入；配置 knowledge_base 目标时明确失败，禁止上报虚假写入计数。
 */
@Component
@Order(40)
public class DataRouter implements ProcessingStep {

    @Override
    public String name() {
        return "DataRouter";
    }

    @Override
    public ProcessingContext execute(ProcessingContext context) {
        var target = context.getConfig().getRouteTarget();
        if (target == null) {
            context.log(name(), "无路由目标，跳过");
            return context;
        }

        switch (target.getType()) {
            case "knowledge_base" ->
                    throw new UnsupportedOperationException(
                            "知识库路由尚未实现，未写入任何数据: " + target.getTarget());
            default -> context.log(name(), "未知目标类型: " + target.getType());
        }
        return context;
    }
}
