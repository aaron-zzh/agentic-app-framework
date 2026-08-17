package com.xuejiai.aaf.framework.intelligent.assistant.port;

import com.xuejiai.aaf.framework.intelligent.assistant.model.SkillDecisionAuditEvent;

/** 追加式 Skill 决策审计输出端口。 */
@FunctionalInterface
public interface SkillDecisionAuditPort {

    void append(SkillDecisionAuditEvent event);

    static SkillDecisionAuditPort noop() {
        return ignored -> {};
    }
}
