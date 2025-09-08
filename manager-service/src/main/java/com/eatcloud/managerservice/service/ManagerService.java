package com.eatcloud.managerservice.service;

import java.util.Collection;
import java.util.UUID;

import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.managerservice.dto.SignupRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eatcloud.managerservice.dto.ManagerDto;
import com.eatcloud.managerservice.entity.Manager;
import com.eatcloud.managerservice.error.ManagerErrorCode;
import com.eatcloud.managerservice.repository.ManagerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.eatcloud.managerservice.dto.UserDto;

@Service
@RequiredArgsConstructor
@Slf4j
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class ManagerService {
	private final ManagerRepository managerRepository;

	@Transactional
	public void remove(UUID id) { // actor는 보통 로그인 유저 id
		managerRepository.softDeleteById(id, "김xx");
	}

	@Transactional
	public int bulkRemove(Collection<UUID> ids, String actor) {
		return managerRepository.softDeleteAllByIds(ids, actor); // 벌크 UPDATE (리스너 미탐)
	}

	@Transactional
	public int restore(Collection<UUID> ids) {
		return managerRepository.restoreAllByIds(ids); // 복구
	}

	@Transactional
	public void signup(SignupRequestDto request) {
		try {
			Manager manager = new Manager();
			manager.setEmail(request.getEmail());
			manager.setPassword(request.getPassword());
			manager.setName(request.getName());
			manager.setPhoneNumber(request.getPhone());

			log.info("Manager 엔티티 생성 완료: {}", manager);

			Manager savedManager = managerRepository.save(manager);
			log.info("데이터베이스 저장 완료: savedManager={}", savedManager);

			// 저장된 데이터 재확인
			Manager foundManager = managerRepository.findById(savedManager.getId()).orElse(null);
			log.info("저장된 데이터 재확인: foundManager={}", foundManager);

			log.info("=== 회원가입 완료 ===");
		} catch (Exception e) {
			log.error("=== 회원가입 실패 ===");
			log.error("오류 발생: {}", e.getMessage(), e);
			throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public ManagerDto getOrThrow(UUID id) {
		Manager e = managerRepository.findByIdAndDeletedAtIsNull(id)
			.orElseThrow(() -> new com.eatcloud.autoresponse.error.BusinessException(
				ManagerErrorCode.MANAGER_NOT_FOUND));
		return ManagerDto.from(e); // 또는 Mapper 사용
	}

	public UserDto findByEmail(String email) {
		Manager manager = managerRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Manager not found"));

		return UserDto.builder()
				.id(manager.getId())
				.email(manager.getEmail())
				.password(manager.getPassword())
				.name(manager.getName())
				.role("manager")
				.build();
	}
}

