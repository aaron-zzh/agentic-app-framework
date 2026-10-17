package com.xuejiai.aaf.module.elder.guard.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.guard.domain.AlertEvent;
import com.xuejiai.aaf.module.elder.guard.vo.AlertEventCreateDTO;

/** 预警创建对象转换器；状态、接收时间和下一动作由服务层设置。 */
@Mapper
public interface AlertEventConvert {

    AlertEventConvert INSTANCE = Mappers.getMapper(AlertEventConvert.class);

    AlertEvent toEntity(AlertEventCreateDTO dto);
}
