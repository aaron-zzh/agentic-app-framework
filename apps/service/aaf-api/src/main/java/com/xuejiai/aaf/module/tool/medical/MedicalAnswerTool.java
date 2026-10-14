package com.xuejiai.aaf.module.tool.medical;

import java.util.List;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.module.tool.websearch.ToolQueryGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 医疗问答工具——基于权威医疗数据搜索并由大模型汇总，返回结论型解答及依据来源（MedicalAnswer）。
 *
 * <p>工具名：{@code queryMedicalAnswer}。解答是第三方大模型生成的内容，仅供健康知识参考，不构成诊疗建议。
 * 问题会发送到第三方服务，因此拒绝含手机号、身份证号的查询词，并在工具描述中要求调用方只传脱敏后的通用问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MedicalAnswerTool {

    static final String TOOL_NAME = "queryMedicalAnswer";

    /** 与 IQS 接口限制一致（1~200 字符）；超长不会报错但效果不保证，故在客户侧拒绝。 */
    private static final int MAX_QUERY_LENGTH = 200;

    /** 回灌给模型的解答正文与来源摘要上限，控制上下文体积。 */
    private static final int MAX_ANSWER_LENGTH = 3000;

    private static final int MAX_SOURCE_CONTENT_LENGTH = 300;

    private static final String DISCLAIMER =
            "以下解答由第三方大模型基于医疗数据汇总生成，仅供健康知识参考，不构成诊疗建议，不能替代执业医师的面诊与处方。";

    private final MedicalAnswerClient client;

    @Tool(
            description =
                    "回答口语化的健康知识问题（如“坐车恶心的原因是什么”“无糖可乐对身体好吗”“肚子疼怎么办”），"
                            + "返回结论型解答及依据来源（医疗网页、指南、论文、图书、药品说明书等）。"
                            + "参数：query（通用的健康问题，必填，建议 30 字以内）。"
                            + "只能作为健康知识参考，禁止据此给出诊断结论、处方或具体用药剂量。"
                            + "回答用户时必须附带“仅供健康知识参考，不构成诊疗建议”的提示，并列出依据来源；"
                            + "出现胸痛、呼吸困难、意识不清、大出血、持续高热等紧急情况时，"
                            + "应直接建议立即就医或拨打急救电话，不要等待检索结果。"
                            + "严禁在 query 中包含用户姓名、手机号、身份证号、住址、具体病史、用药清单等个人隐私信息，"
                            + "只传脱敏后的通用症状或问题。")
    public String queryMedicalAnswer(
            @ToolParam(description = "通用的健康问题，不得包含个人隐私信息，建议 30 字以内") String query) {
        if (query == null || query.isBlank()) {
            return error("INVALID_QUERY", "问题不能为空");
        }
        var trimmed = query.trim();
        if (trimmed.length() > MAX_QUERY_LENGTH) {
            return error("INVALID_QUERY", "问题不能超过 " + MAX_QUERY_LENGTH + " 个字符");
        }
        if (ToolQueryGuard.containsSensitiveNumber(trimmed)) {
            return error("SENSITIVE_QUERY", "问题包含手机号或身份证号，请去除个人隐私信息后重试");
        }
        try {
            var result = client.query(trimmed);
            if (result.answers().isEmpty()) {
                return success(
                        Map.of(
                                "count",
                                0,
                                "disclaimer",
                                DISCLAIMER,
                                "hint",
                                "未检索到可靠解答，请换一种问法，或改用联网搜索；如症状严重请及时就医"));
            }
            List<Map<String, Object>> answers =
                    result.answers().stream()
                            .map(
                                    answer ->
                                            Map.<String, Object>of(
                                                    "answer",
                                                    truncate(answer.content(), MAX_ANSWER_LENGTH),
                                                    "sources",
                                                    answer.sources().stream()
                                                            .map(
                                                                    source ->
                                                                            Map.of(
                                                                                    "title",
                                                                                    source.title(),
                                                                                    "url",
                                                                                    source.url(),
                                                                                    "type",
                                                                                    source.type(),
                                                                                    "content",
                                                                                    truncate(
                                                                                            source
                                                                                                    .content(),
                                                                                            MAX_SOURCE_CONTENT_LENGTH)))
                                                            .toList()))
                            .toList();
            return success(
                    Map.of("count", answers.size(), "disclaimer", DISCLAIMER, "results", answers));
        } catch (Exception e) {
            // 问题可能含用户输入，日志只记录长度，不记录原文
            log.error(
                    "[MedicalAnswerTool] 问答失败: queryLength={}, err={}",
                    trimmed.length(),
                    e.getMessage());
            return error("MEDICAL_ANSWER_ERROR", e.getMessage());
        }
    }

    private static String success(Map<String, Object> output) {
        return JsonUtils.toJsonString(
                ToolCallResult.success(TOOL_NAME, JsonUtils.toJsonString(output)));
    }

    private static String error(String code, String message) {
        return JsonUtils.toJsonString(ToolCallResult.error(TOOL_NAME, code, message));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
