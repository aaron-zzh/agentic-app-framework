package com.xuejiai.aaf.module.ai.persona.outfit.vo;

import com.xuejiai.aaf.common.model.PageParam;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AvatarOutfitPageDTO extends PageParam {
    private String type;
}
