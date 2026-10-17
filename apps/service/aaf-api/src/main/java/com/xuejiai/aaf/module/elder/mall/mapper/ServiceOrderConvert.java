package com.xuejiai.aaf.module.elder.mall.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.mall.domain.ServiceOrder;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceOrderCreateDTO;

/** 服务预约创建对象转换器；状态与处理时间由 Service 设置。 */
@Mapper
public interface ServiceOrderConvert {

    ServiceOrderConvert INSTANCE = Mappers.getMapper(ServiceOrderConvert.class);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reminderCommandId", ignore = true)
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "completedAt", ignore = true)
    @Mapping(target = "cancelledAt", ignore = true)
    ServiceOrder toEntity(ServiceOrderCreateDTO dto);
}
