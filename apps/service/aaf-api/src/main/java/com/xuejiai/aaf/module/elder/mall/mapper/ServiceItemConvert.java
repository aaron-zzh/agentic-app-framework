package com.xuejiai.aaf.module.elder.mall.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.common.enums.elder.ServiceItemStatusEnum;
import com.xuejiai.aaf.module.elder.mall.domain.ServiceItem;
import com.xuejiai.aaf.module.elder.mall.vo.ServiceItemCreateDTO;

/** 服务项目创建对象转换器。 */
@Mapper(imports = ServiceItemStatusEnum.class)
public interface ServiceItemConvert {

    ServiceItemConvert INSTANCE = Mappers.getMapper(ServiceItemConvert.class);

    @Mapping(
            target = "status",
            source = "status",
            defaultExpression = "java(ServiceItemStatusEnum.AVAILABLE.getCode())")
    ServiceItem toEntity(ServiceItemCreateDTO dto);
}
