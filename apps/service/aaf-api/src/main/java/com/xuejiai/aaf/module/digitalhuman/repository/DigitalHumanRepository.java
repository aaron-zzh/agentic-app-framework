package com.xuejiai.aaf.module.digitalhuman.repository;

import java.util.List;

import org.springframework.data.domain.Sort;

import com.xuejiai.aaf.framework.crud.CrudEntityRepository;
import com.xuejiai.aaf.module.digitalhuman.domain.DigitalHuman;

/**
 * 数字人数据访问层
 *
 * @author AaronZZH & Kiro
 */
public interface DigitalHumanRepository extends CrudEntityRepository<DigitalHuman> {
}
