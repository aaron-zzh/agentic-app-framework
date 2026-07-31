package com.xuejiai.aaf.module.approval.repository;

import java.util.List;

import com.xuejiai.aaf.module.approval.domain.CarbonCopyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 抄送记录仓储。
 *
 * @author AaronZZH
 */
public interface CarbonCopyRepository extends JpaRepository<CarbonCopyRecord, Long> {

    /** 查询用户的抄送记录（按时间倒序） */
    List<CarbonCopyRecord> findByCcUserOrderByCcTimeDesc(String ccUser);

    /** 统计用户未读抄送数 */
    long countByCcUserAndReadFalse(String ccUser);
}
