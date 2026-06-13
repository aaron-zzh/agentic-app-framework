package com.xuejiai.aaf.module.system.task.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xuejiai.aaf.module.system.task.domain.SysHolidayCalendar;
import com.xuejiai.aaf.module.system.task.repository.SysHolidayCalendarRepository;

import lombok.RequiredArgsConstructor;

/** 节假日日历服务——判断指定日期是否应排除执行。 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HolidayCalendarService {

    private final SysHolidayCalendarRepository repository;

    /**
     * 判断指定日期是否被排除（节假日跳过）。 调休（WORKDAY）优先级高于节假日（HOLIDAY）——调休当天不跳过。
     *
     * @param date 待判断日期
     * @param calendarCode 日历编码
     * @return true=应跳过执行，false=正常执行
     */
    public boolean isExcluded(LocalDate date, String calendarCode) {
        if (calendarCode == null || calendarCode.isBlank()) return false;
        var records = repository.findByCalendarCodeAndExcludeDate(calendarCode, date);
        if (records.isEmpty()) return false;
        // 有 WORKDAY 记录则不排除（调休补班）
        boolean hasWorkday = records.stream().anyMatch(r -> "WORKDAY".equals(r.getDayType()));
        if (hasWorkday) return false;
        return records.stream().anyMatch(r -> "HOLIDAY".equals(r.getDayType()));
    }

    /** 查询某日历所有排除日期 */
    public List<SysHolidayCalendar> list(String calendarCode) {
        return repository.findByCalendarCodeOrderByExcludeDateAsc(calendarCode);
    }

    /** 添加排除日期 */
    @Transactional
    public SysHolidayCalendar add(String calendarCode, LocalDate date, String dayType) {
        var entry = new SysHolidayCalendar();
        entry.setCalendarCode(calendarCode);
        entry.setExcludeDate(date);
        entry.setDayType(dayType != null ? dayType : "HOLIDAY");
        return repository.save(entry);
    }

    /** 删除排除日期 */
    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
