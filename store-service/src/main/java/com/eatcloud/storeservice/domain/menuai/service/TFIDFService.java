package com.eatcloud.storeservice.domain.menuai.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.storeservice.domain.menu.entity.Menu;
import com.eatcloud.storeservice.domain.menuai.entity.MenuVector;
import com.eatcloud.storeservice.domain.menuai.repository.MenuVectorRepository;


import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class TFIDFService {

	@Autowired
	private MenuVectorRepository menuVectorRepository;

	public void generateAndSaveMenuVector(Menu menu) {
		try {
			String menuName = menu.getMenuName();

			Optional<MenuVector> existingVector = menuVectorRepository.findByMenuName(menuName);
			if (existingVector.isPresent()) {
				log.info("메뉴 벡터가 이미 존재합니다: {}", menuName);
				return;
			}

			Map<String, Double> tfidfVector = calculateTFIDFVector(menuName);

			MenuVector menuVector = MenuVector.builder()
					.menuName(menuName)
					.tfidfVector(tfidfVector)
					.build();
			
			menuVectorRepository.save(menuVector);
			log.info("메뉴 벡터 생성 완료: {} - {}", menuName, tfidfVector);
			
		} catch (Exception e) {
			log.error("메뉴 벡터 생성 실패: {}", menu.getMenuName(), e);
		}
	}

	private Map<String, Double> calculateTFIDFVector(String menuName) {
		Map<String, Integer> termFrequency = calculateTermFrequency(menuName);

		List<MenuVector> allVectors = menuVectorRepository.findAllVectors();
		Map<String, Double> inverseDocumentFrequency = calculateIDF(allVectors, termFrequency.keySet());

		Map<String, Double> tfidfVector = new HashMap<>();
		for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
			String term = entry.getKey();
			int tf = entry.getValue();
			double idf = inverseDocumentFrequency.getOrDefault(term, 0.0);
			double tfidf = tf * idf;

			if (tfidf > 0.01) {
				tfidfVector.put(term, Math.round(tfidf * 100.0) / 100.0);
			}
		}

		return tfidfVector;
	}

	public void deleteMenuVector(String menuName) {
		try {
			Optional<MenuVector> existingVector = menuVectorRepository.findByMenuName(menuName);
			if (existingVector.isPresent()) {
				menuVectorRepository.deleteByMenuName(menuName);
				log.info("메뉴 벡터 삭제 완료: {}", menuName);
			} else {
				log.info("삭제할 메뉴 벡터가 존재하지 않습니다: {}", menuName);
			}
		} catch (Exception e) {
			log.error("메뉴 벡터 삭제 실패: {}", menuName, e);
		}
	}

	private Map<String, Integer> calculateTermFrequency(String menuName) {
		Map<String, Integer> frequency = new HashMap<>();

		String[] words = menuName.split("\\s+");

		for (String word : words) {
			if (word.length() > 1) {
				frequency.put(word, frequency.getOrDefault(word, 0) + 1);
			}
		}

		return frequency;
	}

	private Map<String, Double> calculateIDF(List<MenuVector> allVectors, Set<String> terms) {
		Map<String, Double> idf = new HashMap<>();
		int totalDocuments = allVectors.size() + 1;

		for (String term : terms) {
			int documentsWithTerm = 1;

			for (MenuVector vector : allVectors) {
				if (vector.getTfidfVector() != null &&
					vector.getTfidfVector().containsKey(term)) {
					documentsWithTerm++;
				}
			}

			idf.put(term, Math.log((double) totalDocuments / documentsWithTerm));
		}

		return idf;
	}
}
