package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 积分消费分类枚举，对应字典 credit_transaction_category。标记积分花在哪种 AI 能力，仅 SPEND 类型有意义。 */
@Getter
@AllArgsConstructor
public enum CreditTransactionCategoryEnum implements ArrayValuable<String> {
    CHAT("chat", "文本对话"),
    VISION("vision", "视觉理解"),
    IMAGE_GEN("image_gen", "图像生成"),
    IMAGE_EDIT("image_edit", "图像编辑"),
    OCR("ocr", "OCR 识别"),
    VIDEO("video", "视频生成"),
    SPEECH_TTS("speech_tts", "语音合成"),
    SPEECH_ASR("speech_asr", "语音识别"),
    EMBEDDING("embedding", "向量嵌入"),
    MODEL_3D("model_3d", "3D 生成"),
    MUSIC("music", "音乐生成"),
    AVATAR("avatar", "数字人视频"),
    TOOL("tool", "工具调用"),
    ENTITLEMENT("entitlement", "权益补充"),
    COPYWRITING("copywriting", "文案生成"),
    IMAGE_PROCESS("image_process", "图像处理"),
    OTHER("other", "其他");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(CreditTransactionCategoryEnum::getCode)
                    .toArray(String[]::new);

    /**
     * 路由层 capability（大写常量，见 {@code CapabilityRoutingContext}）→ 字典 code 的别名映射。
     *
     * <p>仅处理路由 key 与字典 code 命名不一致的情况：
     *
     * <ul>
     *   <li>{@code VIDEO_GEN} → {@code video}（字典只用 {@code video}，不带 _GEN 后缀）
     *   <li>{@code MUSIC_GEN} → {@code music}
     * </ul>
     *
     * <p>其余 capability 路由 key toLowerCase 后即与字典 code 对齐（{@code IMAGE_GEN→image_gen}、 {@code
     * SPEECH_TTS→speech_tts}、{@code OCR→ocr} 等），无需别名。
     */
    private static final Map<String, String> CAPABILITY_ALIAS =
            Map.of(
                    "video_gen", "video",
                    "music_gen", "music");

    /**
     * 把路由层 capability 翻译为流水分类 code（小写、与字典 {@code credit_transaction_category} 的 value 对齐），用于落
     * {@code credit_transaction.category} 与 {@code ai_usage_record.capability}。
     *
     * <p>该方法是"路由 capability → 计费 category"的语义边界翻译，不是兼容层——路由 key 用大写常量 （便于代码引用与 IDE 补全），流水落库用小写
     * code（与字典对齐，前端可查中文 label）。
     *
     * <ul>
     *   <li>null / 空 → 返回 {@link #OTHER} 的 code
     *   <li>大写常量 → toLowerCase 后比对枚举（{@code IMAGE_GEN} → {@code image_gen}）
     *   <li>命名差异 → 走 {@link #CAPABILITY_ALIAS}（{@code VIDEO_GEN} → {@code video}）
     *   <li>未匹配到任何枚举 → 原样返回小写形式（让调用方决定是否兜底，而非吞掉数据）
     * </ul>
     */
    public static String fromCapability(String capability) {
        if (capability == null || capability.isBlank()) {
            return OTHER.code;
        }
        String lower = capability.toLowerCase(Locale.ROOT);
        String aliased = CAPABILITY_ALIAS.getOrDefault(lower, lower);
        for (CreditTransactionCategoryEnum e : values()) {
            if (e.code.equals(aliased)) {
                return e.code;
            }
        }
        // 未在枚举中的值（例如新接入但暂未登记），保留原值小写形式，避免吞掉真实数据
        return aliased;
    }

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
