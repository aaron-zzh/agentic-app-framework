package com.xuejiai.aaf.framework.engine.tool.builtin;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.tool.ToolCallDispatcher.ToolCallResult;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrService;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrRequest;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrTask;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.intelligent.core.registry.AiServiceRegistry;
import com.xuejiai.aaf.framework.security.OperatorContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OCR 文字识别工具 — 注册为 Spring AI Tool，可被对话中的 AI 调用。
 *
 * <p>可用控制、积分预检与结算由 {@link com.xuejiai.aaf.framework.engine.credit.AiCreditAspect} 切面统一处理。 工具目录
 * ai_tool_catalog 中 cost_expression/entitlement_code 应置 NULL，避免双扣。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTool {

    private static final String TOOL_NAME = "recognizeOcr";

    private final AiServiceRegistry aiServiceRegistry;
    private final CapabilityRouter capabilityRouter;
    private final OperatorContext operatorContext;

    @Tool(
            description =
                    "从图像中提取文字或结构化信息（OCR）。参数为 JSON：imageUrl 必填；task 可选，可选值：TEXT_RECOGNITION/KEY_INFORMATION_EXTRACTION/TABLE_PARSING/DOCUMENT_PARSING/FORMULA_RECOGNITION/MULTI_LAN；prompt 可选，自定义提示词。")
    public String recognizeOcr(@ToolParam(description = "OCR 识别 JSON 参数") String requestJson) {
        try {
            var req = JsonUtils.parseObject(requestJson, OcrToolRequest.class);

            // 走模型决策链
            Long userId = operatorContext.currentOwnerId().orElse(null);
            var ctx =
                    CapabilityRoutingContext.of(
                            userId, CapabilityRoutingContext.CAP_OCR, req.modelId());
            var model = capabilityRouter.resolve(ctx);
            var service = aiServiceRegistry.get(OcrService.class, model);

            OcrTask task = req.task() != null ? OcrTask.valueOf(req.task()) : null;
            OcrRequest ocrRequest =
                    task != null
                            ? OcrRequest.ofUrl(req.imageUrl(), task)
                            : OcrRequest.ofUrl(req.imageUrl(), req.prompt());
            var result = service.recognize(model, ocrRequest);

            return asJson(
                    ToolCallResult.success(
                            TOOL_NAME,
                            JsonUtils.toJsonString(
                                    new OcrToolResponse(result.text(), result.ocrResult()))));
        } catch (Exception e) {
            log.error("OCR 工具调用失败: {}", e.getMessage(), e);
            return asJson(ToolCallResult.error(TOOL_NAME, "OCR_ERROR", e.getMessage()));
        }
    }

    private String asJson(ToolCallResult result) {
        try {
            return JsonUtils.toJsonString(result);
        } catch (Exception e) {
            return "{\"success\":false,\"code\":\"TOOL_RESULT_SERIALIZE_ERROR\"}";
        }
    }

    public record OcrToolRequest(String imageUrl, String task, String prompt, String modelId) {}

    public record OcrToolResponse(String text, String ocrResult) {}
}
