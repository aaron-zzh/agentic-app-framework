package com.xuejiai.aaf.module.system.task.domain;

import java.time.LocalDate;

import org.hibernate.annotations.SQLDelete;

import com.xuejiai.aaf.common.model.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 节假日日历——存储排除日期，供定时任务跳过执行。 */
@Getter
@Setter
@Entity
@Table(name = "sys_holiday_calendar")
@SQLDelete(
        sql =
                "UPDATE sys_holiday_calendar SET deleted = true, delete_time = CURRENT_TIMESTAMP WHERE id = ?")
public class SysHolidayCalendar extends BaseEntity {

    /** 日历编码（如 CN_HOLIDAY、CUSTOM） */
    @Column(name = "calendar_code", nullable = false, length = 50)
    private String calendarCode;

    /** 排除日期 */
    @Column(name = "exclude_date", nullable = false)
    private LocalDate excludeDate;

    /** 类型：HOLIDAY=节假日（跳过）/ WORKDAY=调休补班（不跳过） 调休优先级高于节假日 */
    @Column(name = "day_type", nullable = false, length = 20)
    private String dayType = "HOLIDAY";
}
