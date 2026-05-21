package com.xuejiai.aaf.framework.sequence.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.xuejiai.aaf.framework.sequence.domain.SystemSequenceDateRange;

public interface SystemSequenceDateRangeRepository
        extends JpaRepository<SystemSequenceDateRange, Long> {

    Optional<SystemSequenceDateRange>
            findBySequenceIdAndDateFromLessThanEqualAndDateToGreaterThanEqual(
                    Long sequenceId, LocalDate date1, LocalDate date2);
}
