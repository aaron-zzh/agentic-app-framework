package com.xuejiai.aaf.module.ai.aigc.project.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.intelligent.agent.port.ContextAwareToolHandler;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocation;
import com.xuejiai.aaf.framework.intelligent.agent.port.ToolInvocationPort.ToolInvocationResult;
import com.xuejiai.aaf.module.ai.aigc.project.api.AssistantBlueprintProposalInput;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.core.type.TypeReference;

/** Assistant Blueprint 逻辑提案提交 Tool；所有受信范围与版本均由 taskId 绑定的服务端 Proposal 解析。 */
@Component
public final class SubmitAssistantBlueprintProposalToolHandler implements ContextAwareToolHandler {

    public static final String TOOL_NAME = "aigc.blueprint.proposal.submit";

    private static final Set<String> ALLOWED_ARGUMENTS =
            Set.of(
                    "projectSpecification",
                    "projectInitialization",
                    "objectSpecs",
                    "relationSpecs",
                    "actions",
                    "deliverableSets",
                    "processPolicy");

    private static final Set<String> TRUSTED_FIELDS =
            Set.of(
                    "id",
                    "planningId",
                    "proposalId",
                    "proposalVersion",
                    "proposalHash",
                    "snapshotHash",
                    "sourceRef",
                    "sourceType",
                    "projectTypeVersion",
                    "channelSpecVersionIds",
                    "executionBindings",
                    "executionBindingVersionId",
                    "canonicalLayer",
                    "direction",
                    "allowedSourceSemanticRoles",
                    "allowedTargetSemanticRoles",
                    "acyclic",
                    "affectsReadiness",
                    "ownerId",
                    "orgId",
                    "workspaceId",
                    "linkedProjectId",
                    "permission",
                    "permissions",
                    "scope");

    private final AssistantBlueprintProposalService proposals;

    public SubmitAssistantBlueprintProposalToolHandler(
            AssistantBlueprintProposalService proposals) {
        this.proposals = Objects.requireNonNull(proposals, "proposals 不能为空");
    }

    @Override
    public String toolName() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "提交当前可信 Task 绑定的逻辑项目 Blueprint；不得提交数据库 ID、发布版本、权限范围、关系规范语义或哈希。";
    }

    @Override
    public boolean reversible() {
        return true;
    }

    @Override
    public Map<String, Object> inputSchema() {
        return JsonUtils.parseObject(
                """
                {
                  "type": "object",
                  "additionalProperties": false,
                  "required": [
                    "projectSpecification",
                    "projectInitialization",
                    "objectSpecs",
                    "relationSpecs",
                    "actions",
                    "deliverableSets",
                    "processPolicy"
                  ],
                  "properties": {
                    "projectSpecification": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["projectTypeCode", "productionMode", "channelCodes"],
                      "properties": {
                        "projectTypeCode": {"type": "string", "minLength": 1},
                        "productionMode": {"type": "string", "minLength": 1},
                        "channelCodes": {"type": "array", "items": {"type": "string", "minLength": 1}},
                        "description": {"type": "string"},
                        "background": {"type": "string"},
                        "targetAudience": {"type": "string"},
                        "requirements": {"type": "array", "items": {"type": "string", "minLength": 1}},
                        "constraints": {"type": "array", "items": {"type": "string", "minLength": 1}},
                        "keyFacts": {"type": "array", "items": {"type": "string", "minLength": 1}},
                        "keyMessages": {"type": "array", "items": {"type": "string", "minLength": 1}},
                        "styleGuidance": {"type": "string"},
                        "successCriteria": {"type": "array", "items": {"type": "string", "minLength": 1}},
                        "deliveryRequirements": {"type": "object"},
                        "locale": {"type": "string"},
                        "additionalContext": {"type": "string"}
                      }
                    },
                    "projectInitialization": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["fields", "documents"],
                      "properties": {
                        "fields": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "additionalProperties": false,
                            "required": ["key", "label"],
                            "properties": {
                              "key": {"type": "string", "enum": ["description", "background", "targetAudience", "requirements", "constraints", "keyFacts", "keyMessages", "styleGuidance", "successCriteria", "deliveryRequirements", "locale", "additionalContext"]},
                              "label": {"type": "string", "minLength": 1},
                              "description": {"type": "string"},
                              "valueType": {"type": "string"},
                              "required": {"type": "boolean"},
                              "aiAssist": {"type": "boolean"},
                              "affectsStructure": {"type": "boolean"},
                              "orderNo": {"type": "integer"},
                              "validation": {"type": "object"}
                            }
                          }
                        },
                        "documents": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "additionalProperties": false,
                            "required": ["key", "role", "label"],
                            "properties": {
                              "key": {"type": "string", "minLength": 1},
                              "role": {"type": "string", "minLength": 1},
                              "label": {"type": "string", "minLength": 1},
                              "description": {"type": "string"},
                              "referenceType": {"type": "string", "enum": ["PROJECT_DOCUMENT", "EXTERNAL_REFERENCE"]},
                              "required": {"type": "boolean"},
                              "minCount": {"type": "integer", "minimum": 0},
                              "maxCount": {"type": "integer", "minimum": 0},
                              "aiAssist": {"type": "boolean"},
                              "contextPolicy": {
                                "type": "object",
                                "additionalProperties": false,
                                "properties": {
                                  "mode": {"type": "string", "enum": ["FULL_TEXT", "SUMMARY", "RAG", "AUTO", "NONE"]},
                                  "priority": {"type": "integer", "minimum": 0},
                                  "maxTokens": {"type": "integer", "minimum": 0}
                                }
                              },
                              "orderNo": {"type": "integer"}
                            }
                          }
                        }
                      }
                    },
                    "objectSpecs": {
                      "type": "array",
                      "minItems": 1,
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["stableKey", "objectType", "displayName", "contractRole", "semanticRole", "outputContract", "adoptionPolicy"],
                        "properties": {
                          "stableKey": {"type": "string", "minLength": 1},
                          "objectType": {"type": "string", "minLength": 1},
                          "displayName": {"type": "string", "minLength": 1},
                          "parentKey": {"type": "string"},
                          "orderNo": {"type": "integer"},
                          "contractRole": {"type": "string", "enum": ["REQUIRED", "OPTIONAL", "EXCLUDED"]},
                          "semanticRole": {"type": "string", "enum": ["STRUCTURE", "WORKING_ARTIFACT", "DELIVERABLE", "EVIDENCE", "REFERENCE"]},
                          "outputContract": {"type": "string", "enum": ["INLINE_OBJECT_VERSION", "DOCUMENT_VERSION", "MEDIA_VERSION", "COMPOSITE_MANIFEST", "REFERENCE_ONLY"]},
                          "adoptionPolicy": {"type": "string", "minLength": 1},
                          "defaultActionKey": {"type": "string"},
                          "userInstructionJson": {"type": "string"},
                          "initialPayloadJson": {"type": "string"},
                          "payloadSchemaJson": {"type": "string"}
                        }
                      }
                    },
                    "relationSpecs": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["sourceKey", "targetKey", "relationType"],
                        "properties": {
                          "sourceKey": {"type": "string", "minLength": 1},
                          "targetKey": {"type": "string", "minLength": 1},
                          "relationType": {"type": "string", "minLength": 1},
                          "metadataJson": {"type": "string"}
                        }
                      }
                    },
                    "actions": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["actionKey", "targetTemplateKey", "dependencyTemplateKeys", "requiredContextKeys", "confirmationPolicy", "isDefault", "inputPresetJson"],
                        "properties": {
                          "actionKey": {"type": "string", "minLength": 1},
                          "targetTemplateKey": {"type": "string", "minLength": 1},
                          "dependencyTemplateKeys": {"type": "array", "items": {"type": "string", "minLength": 1}},
                          "requiredContextKeys": {"type": "array", "items": {"type": "string", "minLength": 1}},
                          "confirmationPolicy": {"type": "string", "enum": ["REQUIRED", "OPTIONAL"]},
                          "isDefault": {"type": "boolean"},
                          "inputPresetJson": {"type": "string"}
                        }
                      }
                    },
                    "deliverableSets": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "additionalProperties": false,
                        "required": ["setTemplateKey", "allowedSlotTemplateKeys", "allowedCustomObjectTypes", "defaultUserAddedContractRole", "completionMode", "reviewMode", "publicationPolicy"],
                        "properties": {
                          "setTemplateKey": {"type": "string", "minLength": 1},
                          "allowedSlotTemplateKeys": {"type": "array", "items": {"type": "string", "minLength": 1}},
                          "allowedCustomObjectTypes": {"type": "array", "items": {"type": "string", "minLength": 1}},
                          "defaultUserAddedContractRole": {"type": "string", "minLength": 1},
                          "completionMode": {"type": "string", "minLength": 1},
                          "reviewMode": {"type": "string", "minLength": 1},
                          "publicationPolicy": {"type": "string", "enum": ["NONE", "OPTIONAL", "AT_LEAST_ONE_SUCCESS", "ALL_SELECTED_CHANNELS"]}
                        }
                      }
                    },
                    "processPolicy": {
                      "type": "object",
                      "additionalProperties": false,
                      "required": ["reviewRequired", "publicationPolicy", "autoActionKeys", "confirmationGateKeys"],
                      "properties": {
                        "reviewRequired": {"type": "boolean"},
                        "publicationPolicy": {"type": "string", "enum": ["NONE", "OPTIONAL", "AT_LEAST_ONE_SUCCESS", "ALL_SELECTED_CHANNELS"]},
                        "autoActionKeys": {"type": "array", "items": {"type": "string", "minLength": 1}},
                        "confirmationGateKeys": {"type": "array", "items": {"type": "string", "minLength": 1}}
                      }
                    }
                  }
                }
                """,
                new TypeReference<Map<String, Object>>() {});
    }

    @Override
    public Mono<ToolInvocationResult> invoke(ToolInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation 不能为空");
        return Mono.fromCallable(() -> submit(invocation)).subscribeOn(Schedulers.boundedElastic());
    }

    private ToolInvocationResult submit(ToolInvocation invocation) {
        var context = invocation.context();
        if (context.taskId() == null) {
            throw new IllegalStateException(TOOL_NAME + " 只能由 canonical Task 调用");
        }
        var arguments = invocation.arguments();
        if (!arguments.keySet().equals(ALLOWED_ARGUMENTS)) {
            throw new IllegalArgumentException(TOOL_NAME + " 参数必须且只能包含 " + ALLOWED_ARGUMENTS);
        }
        rejectTrustedFields(arguments);
        final AssistantBlueprintProposalInput input;
        try {
            input = JsonUtils.convertValue(arguments, AssistantBlueprintProposalInput.class);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(TOOL_NAME + " 逻辑 Blueprint 参数非法", failure);
        }
        var view =
                proposals.fillFromTrustedTask(
                        context.taskId().value(),
                        context.executionId().value(),
                        positiveLong(context.userId().value(), "可信 ownerId"),
                        positiveLong(context.tenantId().value(), "可信 orgId"),
                        context.workspaceId(),
                        input);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("proposalId", view.proposalId());
        metadata.put("proposalVersion", view.proposalVersion());
        metadata.put("proposalHash", view.proposalHash());
        metadata.put("proposalStatus", view.status());
        metadata.put("completionEvidence", "BLUEPRINT_PROPOSAL_VALIDATED");
        metadata.put("reversible", true);
        var output =
                JsonUtils.toJsonString(
                        Map.of(
                                "proposalId", view.proposalId(),
                                "proposalVersion", view.proposalVersion(),
                                "proposalHash", view.proposalHash(),
                                "status", view.status(),
                                "estimatedCredits", view.estimatedCredits()));
        return new ToolInvocationResult(output, metadata);
    }

    private static long positiveLong(String value, String field) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 统一在下方拒绝无效稳定身份。
        }
        throw new IllegalStateException(field + " 不是正整数");
    }

    private static void rejectTrustedFields(Object value) {
        switch (value) {
            case Map<?, ?> map -> {
                for (var entry : map.entrySet()) {
                    var key = String.valueOf(entry.getKey());
                    if (TRUSTED_FIELDS.contains(key)) {
                        throw new IllegalArgumentException(TOOL_NAME + " 不接受受信字段: " + key);
                    }
                    rejectTrustedFields(entry.getValue());
                }
            }
            case Iterable<?> iterable ->
                    iterable.forEach(
                            SubmitAssistantBlueprintProposalToolHandler::rejectTrustedFields);
            case null, default -> {
                // 标量参数无需递归检查。
            }
        }
    }
}
