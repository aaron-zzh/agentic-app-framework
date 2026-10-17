package com.xuejiai.aaf.module.elder.profile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.common.enums.elder.ElderLivingArrangementEnum;
import com.xuejiai.aaf.common.enums.elder.ElderStatusEnum;
import com.xuejiai.aaf.module.elder.profile.domain.Elder;
import com.xuejiai.aaf.module.elder.profile.vo.ElderCreateDTO;

/** 长者档案创建对象转换器；更新由 Patch 应用。 */
@Mapper(imports = {ElderLivingArrangementEnum.class, ElderStatusEnum.class})
public interface ElderConvert {

    ElderConvert INSTANCE = Mappers.getMapper(ElderConvert.class);

    @Mapping(
            target = "livingArrangement",
            source = "livingArrangement",
            defaultExpression = "java(ElderLivingArrangementEnum.ALONE.getCode())")
    @Mapping(
            target = "status",
            source = "status",
            defaultExpression = "java(ElderStatusEnum.ACTIVE.getCode())")
    @Mapping(target = "cameraConsent", source = "cameraConsent", defaultValue = "false")
    Elder toEntity(ElderCreateDTO dto);
}
