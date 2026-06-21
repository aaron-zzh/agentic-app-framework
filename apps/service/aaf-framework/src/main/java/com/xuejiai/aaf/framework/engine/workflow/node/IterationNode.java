package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.List;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;

/**
 * 循环节点——遍历列表变量，每项设置为 currentItem 供子流程使用。
 *
 * <p>BPMN 用法：{@code flowable:delegateExpression="${iterationNode}"}
 *
 * <p>流程变量：
 *
 * <ul>
 *   <li>items（必填）——JSON 数组字符串
 *   <li>maxIterations（可选，默认100）——最大迭代数
 *   <li>currentItem（节点写入）——当前迭代项
 *   <li>iterationIndex（节点写入）——当前迭代索引
 *   <li>iterationResults（节点写入）——所有迭代结果的 JSON 数组
 * </ul>
 */
@Slf4j
@Component("iterationNode")
@RequiredArgsConstructor
public class IterationNode implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        var itemsJson = (String) execution.getVariable("items");
        var maxIterations =
                execution.getVariable("maxIterations") != null
                        ? ((Number) execution.getVariable("maxIterations")).intValue()
                        : 100;

        try {
            List<Object> items = JsonUtils.parseObject(itemsJson, new TypeReference<>() {});
            var limit = Math.min(items.size(), maxIterations);
            var results = new java.util.ArrayList<String>();

            for (int i = 0; i < limit; i++) {
                var item = items.get(i);
                execution.setVariable("currentItem", JsonUtils.toJsonString(item));
                execution.setVariable("iterationIndex", i);
                results.add(JsonUtils.toJsonString(item));
            }

            execution.setVariable("iterationResults", JsonUtils.toJsonString(results));
            execution.setVariable("success", true);
        } catch (Exception e) {
            log.error("循环节点解析 items 失败", e);
            execution.setVariable("success", false);
            execution.setVariable("error", "items 解析失败: " + e.getMessage());
        }
    }
}
