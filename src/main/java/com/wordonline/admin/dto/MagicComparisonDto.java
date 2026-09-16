package com.wordonline.admin.dto;

public record MagicComparisonDto(
        String name,
        boolean primaryPresent,
        boolean secondaryPresent,
        String primaryElement,
        String secondaryElement,
        String primaryAccessType,
        String secondaryAccessType
) {
}
