package com.xuejiai.aaf.module.device.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.device.domain.Device;
import com.xuejiai.aaf.module.device.vo.DeviceCreateDTO;

/** 设备创建对象转换器；硬件信息与凭据由服务层从配对会话填充。 */
@Mapper
public interface DeviceConvert {

    DeviceConvert INSTANCE = Mappers.getMapper(DeviceConvert.class);

    @Mapping(target = "subjectType", ignore = true)
    @Mapping(target = "subjectId", ignore = true)
    Device toEntity(DeviceCreateDTO dto);
}
