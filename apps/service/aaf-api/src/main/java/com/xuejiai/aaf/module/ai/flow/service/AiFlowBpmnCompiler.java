package com.xuejiai.aaf.module.ai.flow.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.exception.BusinessException;
import com.xuejiai.aaf.common.exception.GlobalErrorCode;
import com.xuejiai.aaf.common.util.JsonUtils;

import tools.jackson.databind.JsonNode;

/** 将 flow-editor 编辑态 JSON 编译为可执行且带 DI 的 BPMN 2.0。 */
@Component
public class AiFlowBpmnCompiler {

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]{0,63}");
    private static final int MAX_NODES = 200;
    private static final int MAX_EDGES = 500;

    private static final Map<String, String> DELEGATES =
            Map.of(
                    "llm", "llmNode",
                    "agent", "agentNode",
                    "knowledge", "searchKnowledgeNode",
                    "code", "codeExecutionNode",
                    "iteration", "iterationNode",
                    "http", "httpNode",
                    "tool", "toolNode");

    /** 返回 AI Flow 在 Flowable 中使用的稳定流程 key。 */
    public String processKey(Long flowId) {
        return "ai_flow_" + flowId;
    }

    /** 编译并校验编辑态流程定义。 */
    public String compile(Long flowId, String definition) {
        return compile(flowId, definition, processKey(flowId));
    }

    /** 编译调试版本，使用隔离流程 key，避免覆盖正式部署的最新版本。 */
    public DebugCompilation compileDebug(Long flowId, String runId, String definition) {
        var debugKey = "ai_flow_debug_" + flowId + "_" + runId.replace("-", "");
        if (!ID_PATTERN.matcher(debugKey).matches()) {
            throw badRequest("调试运行标识不合法");
        }
        return new DebugCompilation(debugKey, compile(flowId, definition, debugKey));
    }

    private String compile(Long flowId, String definition, String processKey) {
        var root = parseDefinition(definition);
        var nodes = array(root, "nodes");
        var edges = array(root, "edges");
        validateSize(nodes, edges);

        var nodeById = new HashMap<String, JsonNode>();
        var startCount = 0;
        var endCount = 0;
        for (var node : nodes) {
            var id = requiredText(node, "id");
            var type = requiredText(node, "type");
            validateId(id, "节点");
            if (!isSupportedType(type)) {
                throw badRequest("不支持的工作流节点类型: " + type);
            }
            if (nodeById.put(id, node) != null) {
                throw badRequest("节点 ID 重复: " + id);
            }
            if ("start".equals(type)) startCount++;
            if ("end".equals(type)) endCount++;
        }
        if (startCount != 1 || endCount < 1) {
            throw badRequest("工作流必须且只能有一个开始节点，并至少有一个结束节点");
        }

        var edgeIds = new HashSet<String>();
        var outgoing = new HashMap<String, List<JsonNode>>();
        for (var edge : edges) {
            var id = requiredText(edge, "id");
            var source = requiredText(edge, "source");
            var target = requiredText(edge, "target");
            validateId(id, "连线");
            if (!edgeIds.add(id)) throw badRequest("连线 ID 重复: " + id);
            if (!nodeById.containsKey(source) || !nodeById.containsKey(target)) {
                throw badRequest("连线引用了不存在的节点: " + id);
            }
            outgoing.computeIfAbsent(source, ignored -> new ArrayList<>()).add(edge);
        }

        var defaultEdges = validateGatewayEdges(nodeById, outgoing);
        var signals = collectSignals(nodes);
        return render(flowId, processKey, nodes, edges, nodeById, defaultEdges, signals);
    }

    private String render(
            Long flowId,
            String processKey,
            List<JsonNode> nodes,
            List<JsonNode> edges,
            Map<String, JsonNode> nodeById,
            Map<String, String> defaultEdges,
            Map<String, String> signals) {
        var xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n")
                .append("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n")
                .append("  xmlns:flowable=\"http://flowable.org/bpmn\"\n")
                .append("  xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\"\n")
                .append("  xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\"\n")
                .append("  xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\"\n")
                .append("  targetNamespace=\"http://aaf.xuejiai.com/workflow\">\n");
        signals.forEach(
                (nodeId, waitKey) ->
                        xml.append("  <signal id=\"signal_")
                                .append(escape(nodeId))
                                .append("\" name=\"")
                                .append(escape(waitKey))
                                .append("\"/>\n"));
        xml.append("  <process id=\"")
                .append(processKey)
                .append("\" name=\"AI Flow ")
                .append(flowId)
                .append("\" isExecutable=\"true\">\n");

        for (var node : nodes) {
            renderNode(xml, node, defaultEdges.get(requiredText(node, "id")), signals);
        }
        for (var edge : edges) {
            renderEdge(xml, edge, nodeById.get(requiredText(edge, "source")));
        }
        xml.append("  </process>\n");
        renderDiagram(xml, processKey, nodes, edges, nodeById);
        xml.append("</definitions>\n");
        return xml.toString();
    }

    private void renderNode(
            StringBuilder xml, JsonNode node, String defaultEdge, Map<String, String> signals) {
        var id = requiredText(node, "id");
        var type = requiredText(node, "type");
        var data = node.path("data");
        var name = data.path("label").asText(id);
        switch (type) {
            case "start" -> emptyElement(xml, "startEvent", id, name, null);
            case "end" -> emptyElement(xml, "endEvent", id, name, null);
            case "condition" ->
                    emptyElement(
                            xml,
                            "exclusiveGateway",
                            id,
                            name,
                            defaultEdge == null
                                    ? null
                                    : " default=\"" + escape(defaultEdge) + "\"");
            case "parallel" -> emptyElement(xml, "parallelGateway", id, name, null);
            case "wait" -> renderWait(xml, id, name, data, signals);
            default -> renderServiceTask(xml, id, name, type, data);
        }
    }

    private void renderServiceTask(
            StringBuilder xml, String id, String name, String type, JsonNode data) {
        var delegate = DELEGATES.get(type);
        if (delegate == null) throw badRequest("节点缺少执行器: " + type);
        xml.append("    <serviceTask id=\"")
                .append(escape(id))
                .append("\" name=\"")
                .append(escape(name))
                .append("\" flowable:delegateExpression=\"${")
                .append(delegate)
                .append("}\">\n");
        renderExecutionListeners(xml, data);
        xml.append("    </serviceTask>\n");
    }

    private void renderExecutionListeners(StringBuilder xml, JsonNode data) {
        var configuration =
                cdata(JsonUtils.toJsonString(JsonUtils.convertValue(data, Object.class)));
        xml.append("      <extensionElements>\n")
                .append(
                        "        <flowable:executionListener event=\"start\" delegateExpression=\"${workflowNodeConfigurationListener}\">\n")
                .append(
                        "          <flowable:field name=\"phase\"><flowable:string>start</flowable:string></flowable:field>\n")
                .append(
                        "          <flowable:field name=\"configuration\"><flowable:string><![CDATA[")
                .append(configuration)
                .append("]]></flowable:string></flowable:field>\n")
                .append("        </flowable:executionListener>\n")
                .append(
                        "        <flowable:executionListener event=\"end\" delegateExpression=\"${workflowNodeConfigurationListener}\">\n")
                .append(
                        "          <flowable:field name=\"phase\"><flowable:string>end</flowable:string></flowable:field>\n")
                .append("        </flowable:executionListener>\n")
                .append("      </extensionElements>\n");
    }

    private void renderWait(
            StringBuilder xml, String id, String name, JsonNode data, Map<String, String> signals) {
        var waitType = data.path("waitType").asText("signal");
        if ("human".equals(waitType)) {
            var assignee = data.path("assignee").asText();
            xml.append("    <userTask id=\"")
                    .append(escape(id))
                    .append("\" name=\"")
                    .append(escape(name))
                    .append("\"");
            if (!assignee.isBlank()) {
                xml.append(" flowable:assignee=\"").append(escape(assignee)).append("\"");
            }
            xml.append("/>\n");
            return;
        }
        xml.append("    <intermediateCatchEvent id=\"")
                .append(escape(id))
                .append("\" name=\"")
                .append(escape(name))
                .append("\">\n");
        if ("timer".equals(waitType)) {
            var duration = data.path("waitKey").asText();
            if (duration.isBlank()) throw badRequest("定时等待节点必须配置 ISO-8601 时长");
            xml.append("      <timerEventDefinition><timeDuration>")
                    .append(escape(duration))
                    .append("</timeDuration></timerEventDefinition>\n");
        } else {
            var signalId = "signal_" + id;
            xml.append("      <signalEventDefinition signalRef=\"")
                    .append(escape(signalId))
                    .append("\"/>\n");
        }
        xml.append("    </intermediateCatchEvent>\n");
    }

    private void renderEdge(StringBuilder xml, JsonNode edge, JsonNode sourceNode) {
        var id = requiredText(edge, "id");
        var source = requiredText(edge, "source");
        var target = requiredText(edge, "target");
        var label = edge.path("label").asText();
        xml.append("    <sequenceFlow id=\"")
                .append(escape(id))
                .append("\" sourceRef=\"")
                .append(escape(source))
                .append("\" targetRef=\"")
                .append(escape(target))
                .append("\"");
        if (!label.isBlank()) xml.append(" name=\"").append(escape(label)).append("\"");
        xml.append(">\n");

        var condition = edge.path("condition").asText();
        if ("iteration".equals(sourceNode.path("type").asText())) {
            condition =
                    "loop-out".equals(edge.path("sourceHandle").asText())
                            ? "${hasNextIteration == true}"
                            : "${hasNextIteration == false}";
        }
        if (!condition.isBlank()) {
            xml.append("      <conditionExpression xsi:type=\"tFormalExpression\"><![CDATA[")
                    .append(cdata(normalizeCondition(condition)))
                    .append("]]></conditionExpression>\n");
        }
        xml.append("    </sequenceFlow>\n");
    }

    private void renderDiagram(
            StringBuilder xml,
            String processKey,
            List<JsonNode> nodes,
            List<JsonNode> edges,
            Map<String, JsonNode> nodeById) {
        xml.append("  <bpmndi:BPMNDiagram id=\"Diagram_")
                .append(processKey)
                .append("\"><bpmndi:BPMNPlane id=\"Plane_")
                .append(processKey)
                .append("\" bpmnElement=\"")
                .append(processKey)
                .append("\">\n");
        for (var node : nodes) {
            var id = requiredText(node, "id");
            var x = node.path("position").path("x").asDouble(0);
            var y = node.path("position").path("y").asDouble(0);
            var event = Set.of("start", "end").contains(node.path("type").asText());
            var width = event ? 36 : 160;
            var height = event ? 36 : 80;
            xml.append("    <bpmndi:BPMNShape id=\"")
                    .append(escape(id))
                    .append("_di\" bpmnElement=\"")
                    .append(escape(id))
                    .append("\"><dc:Bounds x=\"")
                    .append(x)
                    .append("\" y=\"")
                    .append(y)
                    .append("\" width=\"")
                    .append(width)
                    .append("\" height=\"")
                    .append(height)
                    .append("\"/></bpmndi:BPMNShape>\n");
        }
        for (var edge : edges) {
            var source = nodeById.get(requiredText(edge, "source"));
            var target = nodeById.get(requiredText(edge, "target"));
            var sourceCenter = center(source);
            var targetCenter = center(target);
            xml.append("    <bpmndi:BPMNEdge id=\"")
                    .append(escape(requiredText(edge, "id")))
                    .append("_di\" bpmnElement=\"")
                    .append(escape(requiredText(edge, "id")))
                    .append("\"><di:waypoint x=\"")
                    .append(sourceCenter[0])
                    .append("\" y=\"")
                    .append(sourceCenter[1])
                    .append("\"/><di:waypoint x=\"")
                    .append(targetCenter[0])
                    .append("\" y=\"")
                    .append(targetCenter[1])
                    .append("\"/></bpmndi:BPMNEdge>\n");
        }
        xml.append("  </bpmndi:BPMNPlane></bpmndi:BPMNDiagram>\n");
    }

    private double[] center(JsonNode node) {
        var event = Set.of("start", "end").contains(node.path("type").asText());
        return new double[] {
            node.path("position").path("x").asDouble(0) + (event ? 18 : 80),
            node.path("position").path("y").asDouble(0) + (event ? 18 : 40)
        };
    }

    private Map<String, String> validateGatewayEdges(
            Map<String, JsonNode> nodeById, Map<String, List<JsonNode>> outgoing) {
        var defaults = new HashMap<String, String>();
        nodeById.forEach(
                (nodeId, node) -> {
                    if (!"condition".equals(node.path("type").asText())) return;
                    var unconditioned =
                            outgoing.getOrDefault(nodeId, List.of()).stream()
                                    .filter(edge -> edge.path("condition").asText().isBlank())
                                    .toList();
                    if (unconditioned.size() > 1) {
                        throw badRequest("条件节点只能有一条无条件默认连线: " + nodeId);
                    }
                    if (unconditioned.size() == 1) {
                        defaults.put(nodeId, requiredText(unconditioned.getFirst(), "id"));
                    }
                });
        return defaults;
    }

    private Map<String, String> collectSignals(List<JsonNode> nodes) {
        var signals = new HashMap<String, String>();
        for (var node : nodes) {
            if (!"wait".equals(node.path("type").asText())) continue;
            var data = node.path("data");
            if (!"signal".equals(data.path("waitType").asText("signal"))) continue;
            var waitKey = data.path("waitKey").asText();
            if (waitKey.isBlank()) throw badRequest("信号等待节点必须配置 waitKey");
            signals.put(requiredText(node, "id"), waitKey);
        }
        return signals;
    }

    private void emptyElement(
            StringBuilder xml, String element, String id, String name, String extraAttributes) {
        xml.append("    <")
                .append(element)
                .append(" id=\"")
                .append(escape(id))
                .append("\" name=\"")
                .append(escape(name))
                .append("\"");
        if (extraAttributes != null) xml.append(extraAttributes);
        xml.append("/>\n");
    }

    private JsonNode parseDefinition(String definition) {
        try {
            var root = JsonUtils.readTree(definition);
            if (root == null || !root.isObject()) throw badRequest("工作流定义必须是 JSON 对象");
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            throw badRequest("工作流定义不是有效 JSON");
        }
    }

    private List<JsonNode> array(JsonNode root, String field) {
        var value = root.path(field);
        if (!value.isArray()) throw badRequest("工作流定义缺少数组字段: " + field);
        var result = new ArrayList<JsonNode>();
        value.forEach(result::add);
        return result;
    }

    private void validateSize(List<JsonNode> nodes, List<JsonNode> edges) {
        if (nodes.isEmpty() || nodes.size() > MAX_NODES) {
            throw badRequest("工作流节点数量必须在 1 到 " + MAX_NODES + " 之间");
        }
        if (edges.size() > MAX_EDGES) {
            throw badRequest("工作流连线数量不能超过 " + MAX_EDGES);
        }
    }

    private boolean isSupportedType(String type) {
        return Set.of(
                        "start",
                        "end",
                        "condition",
                        "parallel",
                        "wait",
                        "llm",
                        "agent",
                        "knowledge",
                        "code",
                        "iteration",
                        "http",
                        "tool")
                .contains(type);
    }

    private String requiredText(JsonNode node, String field) {
        var value = node.path(field).asText();
        if (value.isBlank()) throw badRequest("字段不能为空: " + field);
        return value;
    }

    private void validateId(String id, String label) {
        if (!ID_PATTERN.matcher(id).matches()) {
            throw badRequest(label + " ID 格式不正确: " + id);
        }
    }

    private String normalizeCondition(String condition) {
        var trimmed = condition.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}") ? trimmed : "${" + trimmed + "}";
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String cdata(String value) {
        return value.replace("]]>", "]]]]><![CDATA[>");
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(GlobalErrorCode.BAD_REQUEST, message);
    }

    public record DebugCompilation(String processKey, String bpmnXml) {}
}
