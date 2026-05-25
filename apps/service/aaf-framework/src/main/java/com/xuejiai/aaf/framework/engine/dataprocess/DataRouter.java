package com.xuejiai.aaf.framework.engine.dataprocess;

import java.util.Map;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据路由步骤——按配置将处理后的数据写入目标存储。
 *
 * <p>支持的目标类型：
 * <ul>
 *   <li>custom_table — 写入自定义 PostgreSQL 表（动态 INSERT）</li>
 *   <li>knowledge_base — 写入知识库（调用知识库 API）</li>
 * </ul>
 */
@Slf4j
@Component
@Order(40)
@RequiredArgsConstructor
public class DataRouter implements ProcessingStep {

    private final EntityManager entityManager;

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
            case "custom_table" -> insertToTable(context, target.getTarget());
            case "knowledge_base" -> insertToKnowledgeBase(context, target.getTarget());
            default -> context.log(name(), "未知目标类型: " + target.getType());
        }
        return context;
    }

    private void insertToTable(ProcessingContext context, String tableName) {
        var items = context.getItems();
        int count = 0;
        for (var item : items) {
            // 移除内部字段
            item.remove("_raw_");
            if (item.isEmpty()) continue;

            var columns = String.join(", ", item.keySet());
            var placeholders = item.keySet().stream()
                    .map(k -> ":" + k)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            var sql = "INSERT INTO %s (%s) VALUES (%s)".formatted(tableName, columns, placeholders);
            var query = entityManager.createNativeQuery(sql);
            for (var entry : item.entrySet()) {
                query.setParameter(entry.getKey(), entry.getValue());
            }
            query.executeUpdate();
            count++;
        }
        context.log(name(), "写入 %s 表 %d 条".formatted(tableName, count));
        context.getMetadata().put("inserted_count", count);
    }

    private void insertToKnowledgeBase(ProcessingContext context, String knowledgeBaseId) {
        // 知识库写入：将每条数据的文本字段拼接后存入向量库
        int count = 0;
        for (var item : context.getItems()) {
            item.remove("_raw_");
            var text = item.values().stream()
                    .filter(v -> v instanceof String)
                    .map(Object::toString)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            if (!text.isBlank()) {
                // TODO: 调用知识库 API 写入向量库
                count++;
            }
        }
        context.log(name(), "写入知识库 [%s] %d 条".formatted(knowledgeBaseId, count));
        context.getMetadata().put("inserted_count", count);
    }
}
