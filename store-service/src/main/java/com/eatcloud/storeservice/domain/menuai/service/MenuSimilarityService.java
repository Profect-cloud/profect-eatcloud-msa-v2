package com.eatcloud.storeservice.domain.menuai.service;

import com.eatcloud.storeservice.domain.menuai.dto.MenuSimilarityResult;
import com.eatcloud.storeservice.domain.menuai.entity.MenuVector;
import com.eatcloud.storeservice.domain.menuai.repository.MenuVectorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuSimilarityService {

	private final MenuVectorRepository menuVectorRepository;

	public List<MenuSimilarityResult> findSimilarMenus(String menuName, int limit) {
		try {
			log.info("=== findSimilarMenus 시작 ===");
			log.info("검색할 메뉴명: '{}'", menuName);
			log.info("요청된 개수: {}", limit);

			Optional<MenuVector> baseVector = menuVectorRepository.findByMenuName(menuName);
			if (baseVector.isEmpty()) {
				log.warn("기준 메뉴 벡터를 찾을 수 없습니다: '{}'", menuName);
				log.info("데이터베이스에 저장된 메뉴명들:");
				List<MenuVector> allVectors = menuVectorRepository.findAll();
				allVectors.forEach(vector -> log.info("  - '{}'", vector.getMenuName()));
				return Collections.emptyList();
			}

			log.info("기준 메뉴 벡터 찾음: {}", baseVector.get().getMenuName());

			Map<String, Double> baseTfidfVector = baseVector.get().getTfidfVector();
			log.info("기준 메뉴 벡터 내용: {}", baseTfidfVector);

			List<MenuVector> allVectors = menuVectorRepository.findAll();
			log.info("전체 메뉴 벡터 개수: {}", allVectors.size());

			List<MenuSimilarityResult> results = allVectors.stream()
					.filter(vector -> !vector.getMenuName().equals(menuName))
					.map(vector -> {
						try {
							Map<String, Double> compareVector = vector.getTfidfVector();
							double similarity = calculateEnhancedSimilarity(baseTfidfVector, compareVector);
							
							log.debug("메뉴 '{}'와의 유사도: {}", vector.getMenuName(), similarity);
							
							return MenuSimilarityResult.builder()
									.id(vector.getId())
									.menuName(vector.getMenuName())
									.similarity(similarity)
									.build();
						} catch (Exception e) {
							log.error("벡터 유사도 계산 실패: {}", vector.getMenuName(), e);
							return null;
						}
					})
					.filter(Objects::nonNull)
					.sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity())) // 유사도 높은 순
					.limit(limit)
					.collect(Collectors.toList());

			log.info("=== findSimilarMenus 완료 ===");
			log.info("메뉴 '{}'에 대한 유사 메뉴 {}개를 찾았습니다.", menuName, results.size());
			if (!results.isEmpty()) {
				log.info("상위 3개 결과:");
				results.stream().limit(3).forEach(result -> 
					log.info("  - {} (유사도: {})", result.getMenuName(), result.getSimilarity()));
			}
			return results;

		} catch (Exception e) {
			log.error("=== findSimilarMenus 실패 ===");
			log.error("메뉴명: '{}'", menuName);
			log.error("에러 상세: ", e);
			return Collections.emptyList();
		}
	}

	public List<MenuSimilarityResult> searchMenusByQuery(String query, int limit) {
		try {
			Map<String, Double> queryVector = createQueryVector(query);

			List<MenuVector> allVectors = menuVectorRepository.findAll();

			List<MenuSimilarityResult> results = allVectors.stream()
					.map(vector -> {
						Map<String, Double> menuVector = vector.getTfidfVector();
						double similarity = calculateEnhancedSimilarity(queryVector, menuVector);
						
						return MenuSimilarityResult.builder()
								.id(vector.getId())
								.menuName(vector.getMenuName())
								.similarity(similarity)
								.build();
					})
					.sorted((a, b) -> Double.compare(b.getSimilarity(), a.getSimilarity()))
					.limit(limit)
					.collect(Collectors.toList());

			log.info("쿼리 '{}'에 대한 유사 메뉴 {}개를 찾았습니다.", query, results.size());
			return results;

		} catch (Exception e) {
			log.error("쿼리 검색 실패: {}", query, e);
			return Collections.emptyList();
		}
	}

	private Map<String, Double> createQueryVector(String query) {
		String[] words = query.replaceAll("[^가-힣a-zA-Z0-9\\s]", " ")
				.trim()
				.split("\\s+");
		
		Map<String, Double> queryVector = new HashMap<>();
		for (String word : words) {
			if (word.length() > 1) {
				queryVector.put(word, queryVector.getOrDefault(word, 0.0) + 1.0);
			}
		}
		
		return queryVector;
	}

	private double calculateEnhancedSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
		double cosineSimilarity = calculateCosineSimilarityWithRealVector(vector1, vector2);

		double partialMatchScore = calculatePartialMatchScore(vector1, vector2);

		double finalSimilarity = (cosineSimilarity * 0.6) + (partialMatchScore * 0.4);
		
		return Math.round(finalSimilarity * 1000.0) / 1000.0;
	}

	private double calculateCosineSimilarityWithRealVector(Map<String, Double> vector1, Map<String, Double> vector2) {
		if (vector1.isEmpty() || vector2.isEmpty()) {
			return 0.0;
		}

		try {
			Set<String> allTerms = new HashSet<>();
			allTerms.addAll(vector1.keySet());
			allTerms.addAll(vector2.keySet());

			RealVector v1 = new ArrayRealVector(allTerms.size());
			RealVector v2 = new ArrayRealVector(allTerms.size());

			int index = 0;
			for (String term : allTerms) {
				double val1 = vector1.getOrDefault(term, 0.0);
				double val2 = vector2.getOrDefault(term, 0.0);
				
				v1.setEntry(index, val1);
				v2.setEntry(index, val2);
				index++;
			}

			double dotProduct = v1.dotProduct(v2);
			double norm1 = v1.getNorm();
			double norm2 = v2.getNorm();

			if (norm1 == 0.0 || norm2 == 0.0) {
				return 0.0;
			}

			return dotProduct / (norm1 * norm2);
			
		} catch (Exception e) {
			log.warn("RealVector 코사인 유사도 계산 실패, 기본 방식으로 대체", e);
			return calculateCosineSimilarity(vector1, vector2);
		}
	}

	private double calculateCosineSimilarity(Map<String, Double> vector1, Map<String, Double> vector2) {
		if (vector1.isEmpty() || vector2.isEmpty()) {
			return 0.0;
		}

		Set<String> allTerms = new HashSet<>();
		allTerms.addAll(vector1.keySet());
		allTerms.addAll(vector2.keySet());

		double dotProduct = 0.0;
		double norm1 = 0.0;
		double norm2 = 0.0;

		for (String term : allTerms) {
			double val1 = vector1.getOrDefault(term, 0.0);
			double val2 = vector2.getOrDefault(term, 0.0);
			
			dotProduct += val1 * val2;
			norm1 += val1 * val1;
			norm2 += val2 * val2;
		}

		if (norm1 == 0.0 || norm2 == 0.0) {
			return 0.0;
		}

		return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
	}

	private double calculatePartialMatchScore(Map<String, Double> vector1, Map<String, Double> vector2) {
		double totalScore = 0.0;
		int matchCount = 0;
		
		for (String term1 : vector1.keySet()) {
			for (String term2 : vector2.keySet()) {
				double matchScore = calculateTermSimilarity(term1, term2);
				if (matchScore > 0.0) {
					totalScore += matchScore;
					matchCount++;
				}
			}
		}
		
		return matchCount > 0 ? totalScore / matchCount : 0.0;
	}

	private double calculateTermSimilarity(String term1, String term2) {
		try {
			if (term1 == null || term2 == null) {
				return 0.0;
			}

			if (term1.equals(term2)) {
				return 1.0;
			}

			if (term1.length() > term2.length()) {
				if (term1.contains(term2) && term2.length() > 1) {
					return 0.8;
				}
			} else {
				if (term2.contains(term1) && term1.length() > 1) {
					return 0.8;
				}
			}

			int commonChars = countCommonCharacters(term1, term2);
			int totalChars = Math.max(term1.length(), term2.length());
			
			if (totalChars > 0 && commonChars > 0) {
				double charSimilarity = (double) commonChars / totalChars;
				return charSimilarity > 0.5 ? charSimilarity * 0.6 : 0.0;
			}
			
			return 0.0;
			
		} catch (Exception e) {
			log.warn("단어 유사도 계산 중 오류 발생: '{}' vs '{}'", term1, term2, e);
			return 0.0;
		}
	}

	private int countCommonCharacters(String str1, String str2) {
		Map<Character, Integer> charCount1 = new HashMap<>();
		Map<Character, Integer> charCount2 = new HashMap<>();

		for (char c : str1.toCharArray()) {
			charCount1.put(c, charCount1.getOrDefault(c, 0) + 1);
		}

		for (char c : str2.toCharArray()) {
			charCount2.put(c, charCount2.getOrDefault(c, 0) + 1);
		}

		int commonCount = 0;
		for (char c : charCount1.keySet()) {
			if (charCount2.containsKey(c)) {
				commonCount += Math.min(charCount1.get(c), charCount2.get(c));
			}
		}
		
		return commonCount;
	}
}
