package com.xuejiai.aaf.module.ai.skill;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillActivationMode;

/**
 * 解析内置技能的 Markdown 源文件。
 *
 * <p>文件名必须等于技能 code；frontmatter 每行一个 {@code key: value}，{@code category}、{@code tool}
 * 可重复，其余字段只能出现一次； 正文即技能执行内容。{@code tool} 格式为 {@code 工具名 | 排序 | 用途}，{@code systemBinding} 格式为 {@code
 * 激活方式 | 排序}。
 */
@Component
public class SkillMarkdownSourceParser {

    private static final Set<String> SINGLE_KEYS =
            Set.of(
                    "code",
                    "name",
                    "summary",
                    "version",
                    "visibility",
                    "toolAccessMode",
                    "changeSummary",
                    "instancePrompt",
                    "systemBinding");
    private static final Set<String> REPEATED_KEYS = Set.of("category", "tool");
    private static final Set<String> REQUIRED_KEYS =
            Set.of(
                    "code",
                    "name",
                    "summary",
                    "version",
                    "visibility",
                    "toolAccessMode",
                    "changeSummary");
    private static final Set<String> VISIBILITIES = Set.of("PUBLIC", "INTERNAL");
    private static final Set<String> TOOL_ACCESS_MODES = Set.of("RESTRICT", "INHERIT");

    public SkillSource parse(Resource resource) {
        try (var input = resource.getInputStream()) {
            var raw = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.startsWith("\uFEFF")) {
                throw new IllegalStateException("Skill 源文件禁止 UTF-8 BOM: " + resource);
            }
            var text = raw.replace("\r\n", "\n").replace('\r', '\n');
            if (!text.startsWith("---\n")) {
                throw new IllegalStateException("Skill 源文件缺少 frontmatter: " + resource);
            }
            var end = text.indexOf("\n---\n", 4);
            if (end < 0) {
                throw new IllegalStateException("Skill 源文件缺少 frontmatter 结束符: " + resource);
            }
            var single = new LinkedHashMap<String, String>();
            var repeated = new LinkedHashMap<String, List<String>>();
            for (var line : text.substring(4, end).split("\n")) {
                var separator = line.indexOf(':');
                if (separator <= 0) {
                    throw new IllegalStateException("Skill frontmatter 行非法: " + resource);
                }
                var key = line.substring(0, separator).trim();
                var value = line.substring(separator + 1).trim();
                if (value.isEmpty()) {
                    throw new IllegalStateException(
                            "Skill frontmatter 字段不能为空: " + key + " @ " + resource);
                }
                if (REPEATED_KEYS.contains(key)) {
                    repeated.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
                } else if (!SINGLE_KEYS.contains(key) || single.put(key, value) != null) {
                    throw new IllegalStateException(
                            "Skill frontmatter 字段非法或重复: " + key + " @ " + resource);
                }
            }
            if (!single.keySet().containsAll(REQUIRED_KEYS)) {
                throw new IllegalStateException(
                        "Skill frontmatter 缺少必填字段 " + REQUIRED_KEYS + ": " + resource);
            }
            var code = single.get("code");
            if (!code.matches("[a-z][a-z0-9._-]{2,99}")) {
                throw new IllegalStateException("Skill code 非法: " + resource);
            }
            if (!(code + ".md").equals(resource.getFilename())) {
                throw new IllegalStateException("Skill 文件名必须等于 code: " + resource);
            }
            var version = Integer.parseInt(single.get("version"));
            if (version != 1) {
                throw new IllegalStateException("Skill 源文件当前只支持 version: 1: " + resource);
            }
            var visibility = single.get("visibility");
            if (!VISIBILITIES.contains(visibility)) {
                throw new IllegalStateException(
                        "Skill visibility 仅允许 PUBLIC、INTERNAL: " + resource);
            }
            var toolAccessMode = single.get("toolAccessMode");
            if (!TOOL_ACCESS_MODES.contains(toolAccessMode)) {
                throw new IllegalStateException(
                        "Skill toolAccessMode 仅允许 RESTRICT、INHERIT: " + resource);
            }
            requireLength(single.get("name"), 128, "name", resource);
            requireLength(single.get("summary"), 512, "summary", resource);
            var content = text.substring(end + 5).strip();
            if (content.isEmpty()) {
                throw new IllegalStateException("Skill 内容不能为空: " + resource);
            }
            return new SkillSource(
                    code,
                    single.get("name"),
                    single.get("summary"),
                    version,
                    visibility,
                    toolAccessMode,
                    single.get("changeSummary"),
                    single.get("instancePrompt"),
                    List.copyOf(repeated.getOrDefault("category", List.of())),
                    parseBinding(single.get("systemBinding"), resource),
                    parseTools(repeated.getOrDefault("tool", List.of()), resource),
                    content,
                    sha256(content),
                    resource.getDescription());
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) {
                throw stateException;
            }
            throw new IllegalStateException("Skill 源文件不可读: " + resource, exception);
        }
    }

    private static SystemBinding parseBinding(String value, Resource resource) {
        if (value == null) {
            return null;
        }
        var parts = split(value, 2, "systemBinding", resource);
        return new SystemBinding(
                SkillActivationMode.valueOf(parts[0]).name(), Integer.parseInt(parts[1]));
    }

    private static List<ToolRequirement> parseTools(List<String> values, Resource resource) {
        var tools = new ArrayList<ToolRequirement>();
        var names = new java.util.HashSet<String>();
        for (var value : values) {
            var parts = split(value, 3, "tool", resource);
            requireLength(parts[0], 128, "tool", resource);
            requireLength(parts[2], 512, "tool usagePurpose", resource);
            if (!names.add(parts[0])) {
                throw new IllegalStateException("Skill tool 重复: " + parts[0] + " @ " + resource);
            }
            tools.add(new ToolRequirement(parts[0], Integer.parseInt(parts[1]), parts[2]));
        }
        return List.copyOf(tools);
    }

    private static String[] split(String value, int count, String key, Resource resource) {
        var parts = value.split("\\|", count);
        if (parts.length != count) {
            throw new IllegalStateException("Skill " + key + " 格式非法: " + resource);
        }
        for (var i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
            if (parts[i].isEmpty()) {
                throw new IllegalStateException("Skill " + key + " 含空字段: " + resource);
            }
        }
        return parts;
    }

    private static void requireLength(String value, int max, String key, Resource resource) {
        if (value.length() > max) {
            throw new IllegalStateException("Skill " + key + " 超过 " + max + " 字符: " + resource);
        }
    }

    private static String sha256(String content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    /** 工具要求：工具名、排序与用途；初始版本一律为必需（required）。 */
    public record ToolRequirement(String toolName, int sortOrder, String usagePurpose) {}

    /** 系统级默认绑定：激活方式与排序。 */
    public record SystemBinding(String activationMode, int sortOrder) {}

    public record SkillSource(
            String code,
            String name,
            String summary,
            int version,
            String visibility,
            String toolAccessMode,
            String changeSummary,
            String instancePrompt,
            List<String> categories,
            SystemBinding systemBinding,
            List<ToolRequirement> tools,
            String content,
            String contentHash,
            String source) {}
}
