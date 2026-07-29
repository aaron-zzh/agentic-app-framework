package com.xuejiai.aaf.common.enums.content;

import java.util.Arrays;

import com.xuejiai.aaf.common.enums.ArrayValuable;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 内容项目对象类型。
 *
 * @author AaronZZH & Kiro
 */
@Getter
@AllArgsConstructor
public enum ContentObjectTypeEnum implements ArrayValuable<String> {
    BRIEF("brief", "简报"),
    CREATIVE_CONCEPT("creative_concept", "创意方向"),
    DELIVERABLE_SET("deliverable_set", "内容包"),
    IMAGE_DELIVERABLE("image_deliverable", "图片交付物"),
    VIDEO_DELIVERABLE("video_deliverable", "视频交付物"),
    COPY_DELIVERABLE("copy_deliverable", "文案交付物"),
    EPISODE("episode", "分集"),
    SCENE("scene", "场次"),
    SHOT("shot", "镜头"),
    SHOT_KEYFRAME("shot_keyframe", "镜头关键帧"),
    REVIEW("review", "审核"),
    INSPIRATION_BOARD("inspiration_board", "灵感板"),
    PROPERTY_SUBJECT("property_subject", "楼盘资料"),
    CLAIM_EVIDENCE("claim_evidence", "主张证据");

    private final String code;
    private final String label;

    public static final String[] ARRAYS =
            Arrays.stream(values()).map(ContentObjectTypeEnum::getCode).toArray(String[]::new);

    @Override
    public String[] array() {
        return ARRAYS;
    }
}
