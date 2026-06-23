/*
 * Copyright 2024-2026 xuejiai.com & AaronZZH.
 * Licensed under the Apache License, Version 2.0.
 */
package com.xuejiai.aaf.framework.agentscope.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xuejiai.aaf.framework.intelligent.ai.ocr.OcrServiceFactory;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrRequest;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRouter;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

/**
 * OCR 文字识别工具——agentscope {@code @Tool} 包装 {@link OcrServiceFactory}。
 *
 * <p>当用户说"帮我识别这张图"时，模型应先引导用户提供图片 URL，然后调用此工具。 工具描述里包含了图片格式要求，模型会在 Function Calling 参数里要求用户提供 URL。
 */
public class OcrAgentTool {

    private static final Logger log = LoggerFactory.getLogger(OcrAgentTool.class);

    private final OcrServiceFactory ocrServiceFactory;
    private final CapabilityRouter capabilityRouter;

    public OcrAgentTool(OcrServiceFactory ocrServiceFactory, CapabilityRouter capabilityRouter) {
        this.ocrServiceFactory = ocrServiceFactory;
        this.capabilityRouter = capabilityRouter;
    }

    @Tool(
            description =
                    "对图片进行 OCR 文字识别。调用前必须确认用户已提供图片 URL（支持格式：JPEG/PNG/WEBP/HEIC/TIFF）。"
                            + "如用户尚未提供图片，先提示：「请提供需要识别的图片链接」，等用户回复后再调用。"
                            + "返回识别出的文字内容。")
    public String ocr_recognize(
            @ToolParam(name = "imageUrl", description = "图片 URL（必须以 .jpg/.png/.webp 等后缀结尾或可公网访问）")
                    String imageUrl,
            @ToolParam(name = "prompt", description = "识别提示词，如「识别所有文字」「提取表格数据」「识别身份证信息」；留空使用默认通用识别")
                    String prompt) {
        log.info("[OcrTool] imageUrl={} prompt={}", imageUrl, prompt);

        if (imageUrl == null || imageUrl.isBlank()) {
            return "{\"status\":\"error\",\"message\":\"请提供图片 URL\"}";
        }

        try {
            // 走六层模型决策链选 OCR 模型
            var ctx = CapabilityRoutingContext.ofCapability(null, CapabilityRoutingContext.CAP_OCR);
            var model = capabilityRouter.resolve(ctx);
            var ocrService = ocrServiceFactory.getService(model);

            var request =
                    (prompt != null && !prompt.isBlank())
                            ? OcrRequest.ofUrl(imageUrl, prompt)
                            : OcrRequest.ofUrl(imageUrl);

            request.validate();
            var result = ocrService.recognize(model, request);

            return "{\"status\":\"ok\",\"text\":" + jsonEscape(result.text()) + "}";
        } catch (Exception e) {
            log.warn("[OcrTool] 识别失败: {}", e.getMessage());
            return "{\"status\":\"error\",\"message\":" + jsonEscape(e.getMessage()) + "}";
        }
    }

    private static String jsonEscape(String s) {
        if (s == null) return "null";
        return "\""
                + s.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "")
                + "\"";
    }
}
