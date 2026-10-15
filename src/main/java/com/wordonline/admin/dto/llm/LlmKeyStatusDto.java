package com.wordonline.admin.dto.llm;

import java.time.LocalDateTime;

/** 저장된 key 의 상태만 담는다. 평문과 암호문은 이 객체로 나가지 않는다. */
public record LlmKeyStatusDto(String provider, String keyHint, LocalDateTime updatedAt) {
}
