package com.xuejiai.aaf.module.channel.service.adapter;

import java.util.HashMap;
import java.util.Map;

/**
 * 机器人指令解析器。
 *
 * <p>解析 /命令 参数1 参数2 格式，结果填充到 UnifiedMessage.extra 中。
 * extra 中 key：command（指令名）、args（参数列表）、isCommand（是否为指令消息）。
 */
public final class BotCommandParser {

    private BotCommandParser() {}

    /**
     * 解析消息文本中的指令。
     *
     * @param text 消息文本
     * @return extra map，包含 command/args/isCommand
     */
    public static Map<String, Object> parse(String text) {
        var extra = new HashMap<String, Object>();
        if (text == null || text.isBlank()) {
            extra.put("isCommand", false);
            return extra;
        }
        var trimmed = text.strip();
        if (!trimmed.startsWith("/")) {
            extra.put("isCommand", false);
            return extra;
        }
        var parts = trimmed.split("\\s+");
        extra.put("isCommand", true);
        extra.put("command", parts[0].substring(1)); // 去掉 /
        var args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        extra.put("args", args);
        return extra;
    }
}
