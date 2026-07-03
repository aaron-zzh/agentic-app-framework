package com.xuejiai.aaf.framework.intelligent.ai.ocr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.OcrOptions;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.utils.Constants;
import com.google.gson.JsonObject;

import com.xuejiai.aaf.common.exception.ExceptionUtil;
import com.xuejiai.aaf.common.util.JsonUtils;
import com.xuejiai.aaf.framework.engine.credit.AiCreditGuard;
import com.xuejiai.aaf.framework.intelligent.ai.AiErrorCode;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrRequest;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrResult;
import com.xuejiai.aaf.framework.intelligent.ai.ocr.vo.OcrTask;
import com.xuejiai.aaf.framework.intelligent.core.model.AiModel;
import com.xuejiai.aaf.framework.intelligent.core.model.CapabilityRoutingContext;
import com.xuejiai.aaf.framework.security.OperatorContext;

import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

/**
 * 基于百炼 DashScope SDK 的 OCR 服务实现。
 *
 * <p>积分由 {@link OcrServiceDecorator} 装饰器统一处理，本类只负责业务逻辑。
 */
@Slf4j
@Service("dashScopeOcrService")
@ConditionalOnProperty(name = "spring.ai.dashscope.api-key", matchIfMissing = false)
public class DashScopeOcrService implements OcrService {

    static {
        Constants.baseHttpApiUrl = "https://dashscope.aliyuncs.com/api/v1";
    }

    private final String apiKey;
    private final AiCreditGuard creditGuard;
    private final OperatorContext operatorContext;
    private final MultiModalConversation multiModalConv = new MultiModalConversation();

    public DashScopeOcrService(
            @Value("${spring.ai.dashscope.api-key:}") String apiKey,
            AiCreditGuard creditGuard,
            OperatorContext operatorContext) {
        this.apiKey = apiKey;
        this.creditGuard = creditGuard;
        this.operatorContext = operatorContext;
    }

    /**
     * 覆写积分估算：base64 场景按 Qwen smart_resize 规则计算图像 token。
     *
     * <p>URL 场景无法同步读取尺寸，回退到父类估算。
     */
    @Override
    public long estimateCost(AiModel model, Object req, int markupRate) {
        if (!(req instanceof OcrRequest request)) {
            return defaultEstimateCost(model, req, markupRate);
        }
        if (request.imageWidth() != null && request.imageHeight() != null) {
            long token =
                    calcImageToken(
                            request.imageHeight(),
                            request.imageWidth(),
                            request.minPixels(),
                            request.maxPixels());
            double pricePerK =
                    (model != null && model.getInputPricePerK() != null)
                            ? model.getInputPricePerK().doubleValue()
                            : 0.0005;
            long cost =
                    Math.max(
                            1,
                            Math.round(
                                    token
                                            * pricePerK
                                            / 1000.0
                                            * AiCreditGuard.YUAN_TO_CREDIT
                                            * markupRate));
            log.debug(
                    "OCR 积分预估: {}x{} -> imageToken={}, pricePerK={}, markupRate={}, estimatedCost={}",
                    request.imageWidth(),
                    request.imageHeight(),
                    token,
                    String.format("%.6f", pricePerK),
                    markupRate,
                    cost);
            return cost;
        }
        long cost = defaultEstimateCost(model, req, markupRate);
        log.debug("OCR 积分预估(无宽高，回退父类): estimatedCost={}", cost);
        return cost;
    }

    /** Qwen smart_resize token 计算：对齐 32、缩放到像素范围内，加 2 个视觉标记。 */
    private static long calcImageToken(long height, long width, int minPixels, int maxPixels) {
        long hBar = Math.round((double) height / 32) * 32;
        long wBar = Math.round((double) width / 32) * 32;
        if (hBar * wBar > maxPixels) {
            double beta = Math.sqrt((double) height * width / maxPixels);
            hBar = (long) Math.floor(height / beta / 32) * 32;
            wBar = (long) Math.floor(width / beta / 32) * 32;
        } else if (hBar * wBar < minPixels) {
            double beta = Math.sqrt((double) minPixels / (height * width));
            hBar = (long) Math.ceil(height * beta / 32) * 32;
            wBar = (long) Math.ceil(width * beta / 32) * 32;
        }
        return hBar * wBar / (32 * 32) + 2;
    }

    @Override
    public OcrResult recognize(AiModel model, OcrRequest request) {
        request.validate();
        try {
            // 优先使用模型决策链解析出的 modelName，fallback 到 request 中指定的 modelId
            String modelName =
                    (model != null && model.getModelName() != null)
                            ? model.getModelName()
                            : request.modelId();

            MultiModalConversationParam param = buildParam(modelName, request);
            MultiModalConversationResult result = multiModalConv.call(param);

            int inputTokens = 0;
            int outputTokens = 0;
            if (result.getUsage() != null) {
                inputTokens = safeInt(result.getUsage().getInputTokens());
                outputTokens = safeInt(result.getUsage().getOutputTokens());
            }

            var content = result.getOutput().getChoices().get(0).getMessage().getContent();
            if (content == null || content.isEmpty()) {
                log.debug("OCR 返回空 content，imageUrl={}", request.imageUrl());
                return OcrResult.ofText("", inputTokens, outputTokens);
            }

            Map<String, Object> firstItem = content.get(0);
            log.debug(
                    "OCR 原始 content[0] keys={}, imageUrl={}",
                    firstItem.keySet(),
                    request.imageUrl());

            String text = (String) firstItem.getOrDefault("text", "");
            Object ocrResultRaw = firstItem.get("ocr_result");
            log.debug(
                    "OCR 解析结果: text长度={}, ocrResult={}, tokens={}/{}",
                    text.length(),
                    ocrResultRaw != null ? "有" : "无",
                    inputTokens,
                    outputTokens);

            String ocrResultJson = null;
            if (ocrResultRaw instanceof Map<?, ?> ocrMap) {
                Object wordsInfo = ocrMap.get("words_info");
                if (wordsInfo instanceof java.util.List<?> list) {
                    var simplified =
                            list.stream()
                                    .filter(w -> w instanceof Map)
                                    .map(
                                            w -> {
                                                @SuppressWarnings("unchecked")
                                                var word = (Map<String, Object>) w;
                                                var r =
                                                        new java.util.LinkedHashMap<
                                                                String, Object>();
                                                r.put("text", word.get("text"));
                                                Object rrectObj = word.get("rotate_rect");
                                                if (rrectObj instanceof java.util.List<?> rrect
                                                        && rrect.size() == 5) {
                                                    r.put("box", rrectToBox(rrect));
                                                } else if (word.containsKey("location")) {
                                                    r.put("box", word.get("location"));
                                                }
                                                return r;
                                            })
                                    .toList();
                    ocrResultJson = JsonUtils.toJsonString(simplified);
                } else {
                    ocrResultJson = JsonUtils.toJsonString(ocrResultRaw);
                }
            }

            return OcrResult.ofStructured(text, ocrResultJson, inputTokens, outputTokens);

        } catch (com.xuejiai.aaf.common.exception.BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR 识别失败，imageUrl={}, task={}", request.imageUrl(), request.task(), e);
            throw ExceptionUtil.exception(AiErrorCode.OCR_RECOGNIZE_FAILED, e.getMessage());
        }
    }

    @Override
    public Flowable<String> streamRecognize(AiModel model, OcrRequest request) {
        request.validate();
        String modelName =
                (model != null && model.getModelName() != null)
                        ? model.getModelName()
                        : request.modelId();
        try {
            MultiModalConversationParam param = buildParam(modelName, request);
            param.setIncrementalOutput(true);

            // 最后一帧 usage 即为累计总量
            long[] tokens = {0, 0};
            Long userId = operatorContext.currentOwnerId().orElse(null);

            return multiModalConv
                    .streamCall(param)
                    .doOnNext(
                            item -> {
                                if (item.getUsage() != null) {
                                    tokens[0] = safeInt(item.getUsage().getInputTokens());
                                    tokens[1] = safeInt(item.getUsage().getOutputTokens());
                                }
                            })
                    .map(
                            item -> {
                                var content =
                                        item.getOutput()
                                                .getChoices()
                                                .get(0)
                                                .getMessage()
                                                .getContent();
                                if (content == null || content.isEmpty()) return "";
                                Object text = content.get(0).get("text");
                                return text != null ? text.toString() : "";
                            })
                    .filter(s -> !s.isEmpty())
                    .doOnComplete(
                            () -> {
                                try {
                                    creditGuard.settleByUsage(
                                            userId,
                                            model,
                                            OcrResult.ofText(null, tokens[0], tokens[1]),
                                            CapabilityRoutingContext.CAP_OCR,
                                            "OCR 流式识别");
                                } catch (Exception e) {
                                    log.warn(
                                            "OCR 流式结算失败: userId={}, err={}",
                                            userId,
                                            e.getMessage());
                                }
                            });
        } catch (Exception e) {
            log.error("OCR 流式识别失败，imageUrl={}", request.imageUrl(), e);
            throw ExceptionUtil.exception(AiErrorCode.OCR_RECOGNIZE_FAILED, e.getMessage());
        }
    }

    private MultiModalConversationParam buildParam(String modelName, OcrRequest request)
            throws Exception {
        Map<String, Object> imageItem = new HashMap<>();
        String imageSrc = request.imageUrl() != null ? request.imageUrl() : request.imageBase64();
        imageItem.put("image", imageSrc);
        imageItem.put("min_pixels", request.minPixels());
        imageItem.put("max_pixels", request.maxPixels());
        imageItem.put("enable_rotate", request.enableRotate());

        List<Map<String, Object>> contentItems = new ArrayList<>();
        contentItems.add(imageItem);

        if (request.task() == null && request.prompt() != null) {
            contentItems.add(Map.of("text", request.prompt()));
        }

        MultiModalMessage userMessage =
                MultiModalMessage.builder()
                        .role(Role.USER.getValue())
                        .content(contentItems)
                        .build();

        MultiModalConversationParam param =
                MultiModalConversationParam.builder()
                        .apiKey(apiKey)
                        .model(modelName)
                        .message(userMessage)
                        .build();

        if (request.task() != null) {
            param.setOcrOptions(buildOcrOptions(request));
        }

        return param;
    }

    /** 将旋转矩形 [cx, cy, w, h, angle] 转为4顶点坐标列表 [[x1,y1],[x2,y2],[x3,y3],[x4,y4]]。 */
    private static java.util.List<java.util.List<Integer>> rrectToBox(java.util.List<?> rrect) {
        double cx = toDouble(rrect.get(0));
        double cy = toDouble(rrect.get(1));
        double w = toDouble(rrect.get(2));
        double h = toDouble(rrect.get(3));
        double angle = Math.toRadians(toDouble(rrect.get(4)) % 180);
        double[][] corners = {{-w / 2, -h / 2}, {w / 2, -h / 2}, {w / 2, h / 2}, {-w / 2, h / 2}};
        var result = new java.util.ArrayList<java.util.List<Integer>>();
        for (double[] c : corners) {
            int x = (int) Math.round(cx + c[0] * Math.cos(angle) - c[1] * Math.sin(angle));
            int y = (int) Math.round(cy + c[0] * Math.sin(angle) + c[1] * Math.cos(angle));
            result.add(java.util.List.of(x, y));
        }
        return result;
    }

    private static double toDouble(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0;
    }

    /** 构建 DashScope OCR 任务选项。信息抽取任务需附带 resultSchema，其余任务仅指定 task 类型。 */
    private OcrOptions buildOcrOptions(OcrRequest request) {
        var builder = OcrOptions.builder().task(toSdkTask(request.task()));

        // 信息抽取任务：将 JSON 格式的 resultSchema 转为 Gson JsonObject 传给 SDK
        if (request.task() == OcrTask.KEY_INFORMATION_EXTRACTION
                && request.resultSchema() != null) {
            JsonObject gsonSchema;
            try {
                gsonSchema = toGsonObject(JsonUtils.readTree(request.resultSchema()));
            } catch (Exception e) {
                throw ExceptionUtil.exception(AiErrorCode.OCR_RESULT_SCHEMA_INVALID);
            }
            builder.taskConfig(OcrOptions.TaskConfig.builder().resultSchema(gsonSchema).build());
        }

        return builder.build();
    }

    private OcrOptions.Task toSdkTask(OcrTask task) {
        return switch (task) {
            case ADVANCED_RECOGNITION -> OcrOptions.Task.ADVANCED_RECOGNITION;
            case KEY_INFORMATION_EXTRACTION -> OcrOptions.Task.KEY_INFORMATION_EXTRACTION;
            case TABLE_PARSING -> OcrOptions.Task.TABLE_PARSING;
            case DOCUMENT_PARSING -> OcrOptions.Task.DOCUMENT_PARSING;
            case FORMULA_RECOGNITION -> OcrOptions.Task.FORMULA_RECOGNITION;
            case TEXT_RECOGNITION -> OcrOptions.Task.TEXT_RECOGNITION;
            case MULTI_LAN -> OcrOptions.Task.MULTI_LAN;
        };
    }

    private JsonObject toGsonObject(JsonNode node) {
        JsonObject obj = new JsonObject();
        node.properties().forEach(e -> obj.addProperty(e.getKey(), e.getValue().asString()));
        return obj;
    }

    private int safeInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        return 0;
    }
}
