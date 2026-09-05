package com.xuejiai.aaf.module.ai.aigc.project.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** reservation 创建时冻结的不可变目标对象。 */
@Getter
@Setter
@Entity
@Table(name = "aigc_project_execution_reservation_target")
public class AigcProjectExecutionReservationTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private Long reservationId;

    @Column(name = "project_object_id", nullable = false)
    private Long projectObjectId;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime = LocalDateTime.now();
}
