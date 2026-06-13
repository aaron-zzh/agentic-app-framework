package com.xuejiai.aaf.module.system.task.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.module.system.task.domain.SysHolidayCalendar;

public interface SysHolidayCalendarRepository extends JpaRepository<SysHolidayCalendar, Long> {

    List<SysHolidayCalendar> findByCalendarCodeAndExcludeDate(String calendarCode, LocalDate date);

    List<SysHolidayCalendar> findByCalendarCodeOrderByExcludeDateAsc(String calendarCode);
}
