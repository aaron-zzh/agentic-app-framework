package com.xuejiai.aaf.module.content.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.xuejiai.aaf.module.content.domain.ContentProjectBlueprint;
import com.xuejiai.aaf.module.content.vo.ContentProjectBlueprintCreateDTO;

/**
 * 项目蓝图对象转换器。
 *
 * @author AaronZZH & Kiro
 */
@Mapper
public interface ContentProjectBlueprintConvert {

    ContentProjectBlueprintConvert INSTANCE =
            Mappers.getMapper(ContentProjectBlueprintConvert.class);

    ContentProjectBlueprint toEntity(ContentProjectBlueprintCreateDTO dto);
}
