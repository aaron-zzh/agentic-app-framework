package com.xuejiai.aaf.module.tool.medical;

import java.util.Map;
import java.util.Set;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.module.tool.websearch.ToolQueryGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 医疗知识查询工具——基于权威医疗数据返回药品、病症、食品的实体属性（健康图谱 MedicalKnow）。
 *
 * <p>工具名：{@code queryMedicalKnowledge}。仅用于健康知识参考，不构成诊疗建议；「哪家好」「怎么办」 这类主观或推荐类问句不在能力范围内（返回空结果）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalKnowTool {

    static final String TOOL_NAME = "queryMedicalKnowledge";

    /** 与 IQS 接口限制一致（1~200 字符）。 */
    private static final int MAX_QUERY_LENGTH = 200;

    private static final Set<String> TYPES = Set.of("finding", "food", "medicine");

    private static final String DISCLAIMER = "仅供健康知识参考，不构成诊疗建议，不能替代执业医师的面诊与处方。";

    private final MedicalKnowClient client;

    @Tool(
            description =
                    "查询药品、病症、食品的权威医疗属性（如药品的成分、剂型、适应症、药理作用，食品的热量）。"
                            + "参数：query（实体名称，必填，如“阿莫西林胶囊”“高血压”，建议 30 字以内）、"
                            + "type（可选：medicine=药品，finding=病症，food=食品，不确定时不传，由服务自动识别）。"
                            + "只适合查询某个实体的属性，不适合“怎么办”“哪家好”这类问句。"
                            + "回答用户时必须附带“仅供健康知识参考，不构成诊疗建议”的提示，并引用来源链接。"
                            + "严禁在 query 中包含用户姓名、手机号、身份证号、病史等个人隐私信息，只传实体名称。")
    public String queryMedicalKnowledge(
            @ToolParam(description = "医疗实体名称，如药品名、病症名、食品名，不得包含个人隐私信息") String query,
            @ToolParam(
                            description = "实体类型：medicine（药品）、finding（病症）、food（食品）；不确定可不传",
                            required = false)
                    String type) {
        if (query == null || query.isBlank()) {
            return error("INVALID_QUERY", "实体名称不能为空");
        }
        var trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            return error("INVALID_QUERY", "实体名称不能超过 " + MAX_QUERY_LENGTH + " 个字符");
        }
        if (ToolQueryGuard.containsSensitiveNumber(trimmed)) {
            return error("SENSITIVE_QUERY", "查询词包含手机号或身份证号，请去除个人隐私信息后重试");
        }
        var normalizedType = type == null || type.isBlank() ? null : type.trim().toLowerCase();
        if (normalizedType != null && !TYPES.contains(normalizedType)) {
            return error("INVALID_TYPE", "type 仅支持 medicine、finding、food，或不传");
        }
        try {
            var result = client.query(trimmed, normalizedType);
            var items =
                    result.items().stream()
                            .map(item -> Map.of("content", item.content(), "url", item.url()))
                            .toList();
            var output =
                    items.isEmpty()
                            ? Map.<String, Object>of(
                                    "count",
                                    0,
                                    "disclaimer",
                                    DISCLAIMER,
                                    "hint",
                                    "未识别到对应实体，请确认名称是否准确，或改用联网搜索")
                            : Map.<String, Object>of(
                                    "count", items.size(),
                                    "disclaimer", DISCLAIMER,
                                    "results", items);
            return JsonUtils.toJsonString(
                    ToolCallResult.success(TOOL_NAME, JsonUtils.toJsonString(output)));
        } catch (Exception e) {
            // 查询词可能含用户输入，日志只记录长度，不记录原文
            log.error(
                    "[MedicalKnowTool] 查询失败: queryLength={}, err={}",
                    trimmed.length(),
                    e.getMessage());
            return error("MEDICAL_KNOW_ERROR", e.getMessage());
        }
    }

    private static String error(String code, String message) {
        return JsonUtils.toJsonString(ToolCallResult.error(TOOL_NAME, code, message));
    }
}
