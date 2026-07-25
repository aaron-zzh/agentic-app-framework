package com.xuejiai.aaf.framework.intelligent.infrastructure.cognition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.Assessment;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.Candidate;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ConflictDecision;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ConflictResolution;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.PrivacyLevel;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryGovernancePort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort.RecallQuery;

/** 无 fallback 的确定性记忆治理；低可信或疑似凭证候选直接拒绝。 */
public final class RuleBasedMemoryGovernanceAdapter implements MemoryGovernancePort {

    private final MemoryRecallPort recall;

    public RuleBasedMemoryGovernanceAdapter(MemoryRecallPort recall) {
        this.recall = Objects.requireNonNull(recall, "recall 不能为空");
    }

    @Override
    public List<Candidate> extract(String userMessage, String assistantReply, Instant at) {
        if (userMessage == null || userMessage.isBlank()) return List.of();
        var candidates = new ArrayList<Candidate>();
        for (var sentence : userMessage.split("[。！？!?\\n]+")) {
            var text = sentence.trim();
            if (text.length() >= 8) {
                candidates.add(new Candidate(text, tags(text), at));
            }
        }
        return List.copyOf(candidates);
    }

    @Override
    public Assessment assess(Candidate candidate) {
        var lower = candidate.content().toLowerCase(Locale.ROOT);
        var secret = lower.contains("password") || lower.contains("密码")
                || lower.contains("api key") || lower.contains("token=") || lower.contains("secret");
        var stable = lower.contains("我喜欢") || lower.contains("我偏好")
                || lower.contains("请记住") || lower.contains("以后") || lower.contains("我的");
        var importance = stable ? 0.85 : 0.60;
        var confidence = stable ? 0.90 : 0.72;
        var privacy = secret ? PrivacyLevel.SECRET : PrivacyLevel.PERSONAL;
        var summary = redact(candidate.content());
        return new Assessment(candidate, importance, confidence, privacy, summary,
                !secret, secret ? "疑似凭证或秘密，不允许沉淀" : "通过稳定性与隐私规则");
    }

    @Override
    public ConflictResolution resolve(MemorySubject subject, Assessment assessment, Instant at) {
        var existing = recall.recall(
                new RecallQuery(subject, assessment.candidate().content(), 3, 768, at));
        var normalized = normalize(assessment.candidate().content());
        for (var memory : existing) {
            if (normalize(memory.content()).equals(normalized)) {
                return new ConflictResolution(
                        ConflictDecision.DUPLICATE, memory.memoryId(), "已有相同记忆");
            }
            if (sameGovernedTopic(memory, assessment.candidate())) {
                return new ConflictResolution(
                        ConflictDecision.CONFLICT_REQUIRES_CONFIRMATION,
                        memory.memoryId(),
                        "相同主题存在相反事实，需用户确认纠正");
            }
        }
        return new ConflictResolution(ConflictDecision.ADD, null, "未发现重复或冲突");
    }

    private static boolean sameGovernedTopic(MemoryRecord memory, Candidate candidate) {
        var governedTags = List.of("preference", "decision");
        var sharedGovernedTag = memory.tags().stream()
                .filter(governedTags::contains)
                .anyMatch(candidate.tags()::contains);
        return sharedGovernedTag
                && !normalize(memory.content()).equals(normalize(candidate.content()))
                && conflictKey(memory.content()).equals(conflictKey(candidate.content()));
    }

    private static String conflictKey(String text) {
        return normalize(text).replaceAll("不再|不|取消|停止", "");
    }

    private static List<String> tags(String text) {
        var tags = new ArrayList<String>();
        if (text.contains("喜欢") || text.contains("偏好")) tags.add("preference");
        if (text.contains("决定") || text.contains("选择")) tags.add("decision");
        if (tags.isEmpty()) tags.add("fact");
        return List.copyOf(tags);
    }

    private static String redact(String text) {
        var redacted = text
                .replaceAll(
                        "(?i)(password|token|api[_ -]?key|secret)\\s*[:=]\\s*\\S+",
                        "$1=[REDACTED]")
                .replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "[PHONE]")
                .replaceAll(
                        "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
                        "[EMAIL]");
        return redacted.length() <= 256 ? redacted : redacted.substring(0, 256);
    }

    private static String normalize(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
