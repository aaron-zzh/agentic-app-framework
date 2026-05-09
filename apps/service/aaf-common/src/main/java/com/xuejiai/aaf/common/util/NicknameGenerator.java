package com.xuejiai.aaf.common.util;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

/**
 * 随机昵称生成器。
 *
 * <p>用于用户注册时生成默认昵称，格式为"形容词 + 名词"。
 */
public final class NicknameGenerator {

    private static final Random RANDOM = new SecureRandom();

    private static final List<String> ADJECTIVES = List.of(
            "快乐的", "冷静的", "潇洒的", "积极的", "温柔的", "可爱的", "认真的", "帅气的", "活泼的", "开心的",
            "阳光的", "霸气的", "淡定的", "幸福的", "独特的", "时尚的", "大胆的", "健康的", "沉默的", "甜甜的",
            "酷酷的", "英俊的", "善良的", "激动的", "美好的", "勤奋的", "稳重的", "热情的", "优雅的", "开朗的",
            "清爽的", "文艺的", "乐观的", "神勇的", "聪明的", "坚强的", "友好的", "机智的", "正直的", "踏实的",
            "自信的", "温暖的", "专注的", "勤恳的", "动人的", "明亮的", "大气的", "呆萌的", "天真的", "飘逸的");

    private static final List<String> NOUNS = List.of(
            "月亮", "星星", "太阳", "白云", "彩虹", "大树", "高山", "大海", "飞鸟", "蜜蜂",
            "猫咪", "蝴蝶", "松鼠", "海豚", "熊猫", "金鱼", "兔子", "天鹅", "龙猫", "刺猬",
            "咖啡", "柠檬", "草莓", "芒果", "樱桃", "荔枝", "柚子", "菠萝", "蜜桃", "葡萄",
            "书本", "画笔", "音符", "钻石", "枫叶", "荷花", "玫瑰", "百合", "茉莉", "向日葵",
            "星月", "流沙", "微风", "溪流", "云朵", "烟火", "极光", "晚霞", "朝露", "银河");

    private NicknameGenerator() {}

    /** 生成随机昵称，格式：形容词 + 名词。 */
    public static String generate() {
        String adj = ADJECTIVES.get(RANDOM.nextInt(ADJECTIVES.size()));
        String noun = NOUNS.get(RANDOM.nextInt(NOUNS.size()));
        return adj + noun;
    }
}
