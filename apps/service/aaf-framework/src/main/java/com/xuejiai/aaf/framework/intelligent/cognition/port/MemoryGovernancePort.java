package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.time.Instant;
import java.util.List;

import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.Assessment;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.Candidate;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.ConflictResolution;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.MemorySubject;

/** 记忆候选抽取、质量评估和冲突检测边界。 */
public interface MemoryGovernancePort {

    List<Candidate> extract(String userMessage, String assistantReply, Instant at);

    Assessment assess(Candidate candidate);

    ConflictResolution resolve(MemorySubject subject, Assessment assessment, Instant at);
}
