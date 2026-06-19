package com.xuejiai.aaf.framework.engine.credit;

import org.springframework.data.jpa.repository.JpaRepository;

/** AI 调用用量记录 Repository。 */
public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, Long> {}
