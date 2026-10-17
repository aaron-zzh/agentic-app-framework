package com.xuejiai.aaf.module.elder.profile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.profile.domain.ElderContact;
import com.xuejiai.aaf.module.elder.profile.vo.ElderContactCreateDTO;

/** 长者联系人创建对象转换器；授权范围与更新由 Service 处理。 */
@Mapper
public interface ElderContactConvert {

    ElderContactConvert INSTANCE = Mappers.getMapper(ElderContactConvert.class);

    @Mapping(target = "authorizationScopes", ignore = true)
    @Mapping(target = "priority", source = "priority", defaultValue = "0")
    @Mapping(target = "emergencyContact", source = "emergencyContact", defaultValue = "true")
    ElderContact toEntity(ElderContactCreateDTO dto);
}
