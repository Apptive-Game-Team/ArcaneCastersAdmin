package com.wordonline.admin.dto;

import com.wordonline.admin.entity.magic.Magic;

public record MagicDto(
        long id,
        String name,
        String element,
        String accessType
) {

    public MagicDto(Magic magic) {
        this(
                magic.getId(),
                magic.getName(),
                magic.getElement(),
                magic.getAccessType()
        );
    }
}
