package com.eatcloud.storeservice.domain.menuai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuSimilarityResult {
	private Long id;
	private String menuName;
	private double similarity;
}
