package com.eatcloud.storeservice.domain.store.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiDescriptionRequestDto {
    private String menuName;
    private String menuCategoryCode;
    private List<String> ingredients;
    private List<String> tasteKeywords;
}
