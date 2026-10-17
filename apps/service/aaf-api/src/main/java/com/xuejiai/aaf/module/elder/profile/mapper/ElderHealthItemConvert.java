package com.xuejiai.aaf.module.elder.profile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.elder.profile.domain.ElderHealthItem;
import com.xuejiai.aaf.module.elder.profile.vo.ElderHealthItemCreateDTO;

/** 长者健康条目创建对象转换器。 */
@Mapper
public interface ElderHealthItemConvert {

    ElderHealthItemConvert INSTANCE = Mappers.getMapper(ElderHealthItemConvert.class);

    ElderHealthItem toEntity(ElderHealthItemCreateDTO dto);
}
