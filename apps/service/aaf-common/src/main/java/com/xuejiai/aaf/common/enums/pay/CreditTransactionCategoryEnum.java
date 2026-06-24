package com.xuejiai.aaf.common.enums.pay;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 积分消费分类枚举，对应字典 credit_transaction_category。标记积分花在哪种 AI 能力，仅 SPEND 类型有意义。 */
@Getter
@AllArgsConstructor
public enum CreditTransactionCategoryEnum implements ArrayValuable<String> {
    CHAT("chat", "文本对话"),
    IMAGE_GEN("image_gen", "图像生成"),
    IMAGE_EDIT("image_edit", "图像编辑"),
    OCR("ocr", "OCR 识别"),
    VIDEO("video", "视频生成"),
    SPEECH_TTS("speech_tts", "语音合成"),
    SPEECH_ASR("speech_asr", "语音识别"),
    EMBEDDING("embedding", "向量嵌入"),
    MODEL_3D("model_3d", "3D 生成"),
    AVATAR("avatar", "数字人视频"),
    TOOL("tool", "工具调用"),
    ENTITLEMENT("entitlement", "权益补充"),
    COPYWRITING("copywriting", "文案生成"),
    OTHER("other", "其他");

    /** 编译期常量，供积分能力标识使用。 */
    public static final String CHAT_CODE = "chat";

    public static final String IMAGE_GEN_CODE = "image_gen";
    public static final String IMAGE_EDIT_CODE = "image_edit";
    public static final String OCR_CODE = "ocr";
    public static final String VIDEO_CODE = "video";
    public static final String SPEECH_TTS_CODE = "speech_tts";
    public static final String SPEECH_ASR_CODE = "speech_asr";
    public static final String EMBEDDING_CODE = "embedding";
    public static final String MODEL_3D_CODE = "model_3d";
    public static final String AVATAR_CODE = "avatar";
    public static final String TOOL_CODE = "tool";
    public static final String ENTITLEMENT_CODE = "entitlement";
    public static final String OTHER_CODE = "other";

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values())
                    .map(CreditTransactionCategoryEnum::getCode)
                    .toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
