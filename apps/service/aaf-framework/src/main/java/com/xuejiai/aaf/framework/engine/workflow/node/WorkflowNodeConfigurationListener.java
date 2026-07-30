package com.xuejiai.aaf.framework.engine.workflow.node;

import java.util.Map;
import java.util.Set;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.workflow.runtime.WorkflowExecutionLogger;

import lombok.RequiredArgsConstructor;
import tools.jackson.core.type.TypeReference;

/** 将 BPMN 内嵌的节点配置写入当前 execution，并记录节点执行轨迹。 */
@Component("workflowNodeConfigurationListener")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class WorkflowNodeConfigurationListener implements ExecutionListener {

    private static final Set<String> UI_FIELDS = Set.of("label", "icon", "ports");

    private final WorkflowExecutionLogger executionLogger;

    private Expression phase;
    private Expression configuration;

    @Override
    public void notify(DelegateExecution execution) {
        var currentPhase = String.valueOf(phase.getValue(execution));
        var element = execution.getCurrentFlowElement();
        var nodeId = element.getId();
        var nodeName = element.getName() != null ? element.getName() : nodeId;
        if ("start".equals(currentPhase)) {
            applyConfiguration(execution);
            executionLogger.logNodeStart(
                    execution.getProcessInstanceId(),
                    nodeId,
                    nodeName,
                    stringValue(execution.getVariable("input")));
            return;
        }
        if (Boolean.FALSE.equals(execution.getVariable("success"))) {
            executionLogger.logNodeFailed(
                    execution.getProcessInstanceId(),
                    nodeId,
                    nodeName,
                    stringValue(execution.getVariable("error")));
        } else {
            executionLogger.logNodeComplete(
                    execution.getProcessInstanceId(),
                    nodeId,
                    nodeName,
                    stringValue(execution.getVariable("output")));
        }
    }

    private void applyConfiguration(DelegateExecution execution) {
        if (configuration == null) {
            return;
        }
        var json = String.valueOf(configuration.getValue(execution));
        Map<String, Object> values = JsonUtils.parseObject(json, new TypeReference<>() {});
        if (values == null) {
            return;
        }
        values.forEach(
                (key, value) -> {
                    if (!UI_FIELDS.contains(key) && value != null) {
                        execution.setVariableLocal(key, value);
                    }
                });
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof String string ? string : JsonUtils.toJsonString(value);
    }
}
