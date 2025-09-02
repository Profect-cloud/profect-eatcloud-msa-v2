package com.eatcloud.storeservice.domain.menuai.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eatcloud.storeservice.domain.menuai.dto.MenuSimilarityResult;
import com.eatcloud.storeservice.domain.menuai.service.MenuSimilarityService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/stores/menuai")
@Slf4j
public class MenuAIController {

	@Autowired
	private MenuSimilarityService menuSimilarityService;

	@GetMapping("/recommend/{menuName}")
	public ResponseEntity<List<MenuSimilarityResult>> recommendSimilarMenus(
			@PathVariable String menuName,
			@RequestParam(defaultValue = "3") int limit) {
		
		try {
			log.info("=== 메뉴 추천 요청 시작 ===");
			log.info("요청된 메뉴명: '{}'", menuName);
			log.info("요청된 개수: {}", limit);
			log.info("메뉴명 길이: {}", menuName.length());
			log.info("메뉴명 바이트: {}", java.util.Arrays.toString(menuName.getBytes()));
			
			List<MenuSimilarityResult> similarMenus = 
				menuSimilarityService.findSimilarMenus(menuName, limit);
			
			log.info("=== 메뉴 추천 완료 ===");
			log.info("반환된 결과 개수: {}", similarMenus.size());
			if (!similarMenus.isEmpty()) {
				log.info("첫 번째 결과: {}", similarMenus.get(0));
			}
			
			return ResponseEntity.ok(similarMenus);
			
		} catch (Exception e) {
			log.error("=== 메뉴 추천 실패 ===");
			log.error("메뉴명: '{}'", menuName);
			log.error("에러 상세: ", e);
			return ResponseEntity.badRequest().build();
		}
	}

	@GetMapping("/search")
	public ResponseEntity<List<MenuSimilarityResult>> searchSimilarMenus(
			@RequestParam String query,
			@RequestParam(defaultValue = "5") int limit) {
		
		try {
			log.info("=== 메뉴 검색 요청 시작 ===");
			log.info("검색 쿼리: '{}'", query);
			log.info("요청된 개수: {}", limit);
			log.info("쿼리 길이: {}", query.length());
			log.info("쿼리 바이트: {}", java.util.Arrays.toString(query.getBytes()));
			
			// 검색어와 유사한 메뉴들을 찾아서 추천
			List<MenuSimilarityResult> searchResults = 
				menuSimilarityService.searchMenusByQuery(query, limit);
			
			log.info("=== 메뉴 검색 완료 ===");
			log.info("반환된 결과 개수: {}", searchResults.size());
			if (!searchResults.isEmpty()) {
				log.info("첫 번째 결과: {}", searchResults.get(0));
			}
			
			return ResponseEntity.ok(searchResults);
			
		} catch (Exception e) {
			log.error("=== 메뉴 검색 실패 ===");
			log.error("검색 쿼리: '{}'", query);
			log.error("에러 상세: ", e);
			return ResponseEntity.badRequest().build();
		}
	}
}
