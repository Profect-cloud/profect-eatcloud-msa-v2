package com.eatcloud.customerservice.service;

import com.eatcloud.customerservice.dto.SignupRequestDto;
import com.eatcloud.customerservice.dto.UserDto;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.eatcloud.customerservice.dto.request.ChargePointsRequestDto;
import com.eatcloud.customerservice.dto.request.ChangePasswordRequestDto;
import com.eatcloud.customerservice.dto.request.CustomerProfileUpdateRequestDto;
import com.eatcloud.customerservice.dto.request.CustomerWithdrawRequestDto;
import com.eatcloud.customerservice.dto.response.ChargePointsResponseDto;
import com.eatcloud.customerservice.dto.response.CustomerProfileResponseDto;
import com.eatcloud.customerservice.entity.Customer;
import com.eatcloud.customerservice.error.CustomerErrorCode;
import com.eatcloud.autoresponse.error.BusinessException;
import com.eatcloud.customerservice.repository.CustomerRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(readOnly = true)
@Slf4j
public class CustomerService {

	private final CustomerRepository customerRepository;
	private final PasswordEncoder passwordEncoder;

	private static final Pattern EMAIL_PATTERN = Pattern.compile(
		"^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
	);
	private static final Pattern PHONE_PATTERN = Pattern.compile(
		"^01[0-9]-[0-9]{4}-[0-9]{4}$"
	);

	public CustomerService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
		this.customerRepository = Objects.requireNonNull(customerRepository, "CustomerRepository cannot be null");
		this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder cannot be null");
	}

	public Customer getCustomer(UUID customerId) {
		Objects.requireNonNull(customerId, "Customer ID cannot be null");
		return customerRepository.findById(customerId)
			.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
	}

	public CustomerProfileResponseDto getCustomerProfile(UUID customerId) {
		Customer customer = getCustomer(customerId);
		return CustomerProfileResponseDto.from(customer);
	}

	/**
	 * 고객의 현재 포인트를 조회합니다.
	 */
	public Integer getCustomerPoints(UUID customerId) {
		Customer customer = getCustomer(customerId);
		return customer.getPoints() != null ? customer.getPoints() : 0;
	}

	@Transactional
	public void updateCustomer(UUID customerId, CustomerProfileUpdateRequestDto request) {
		Objects.requireNonNull(customerId, "Customer ID cannot be null");
		Objects.requireNonNull(request, "Update request cannot be null");

		Customer customer = customerRepository.findById(customerId)
			.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

		validateUpdateRequest(customer, request);
		applyProfileUpdates(customer, request);
		customerRepository.save(customer);
	}

	@Transactional
	public void withdrawCustomer(UUID customerId, CustomerWithdrawRequestDto request) {
		Objects.requireNonNull(customerId, "Customer ID cannot be null");
		Objects.requireNonNull(request, "Withdraw request cannot be null");

		if (!StringUtils.hasText(request.reason())) {
			throw new BusinessException(CustomerErrorCode.WITHDRAWAL_REASON_REQUIRED);
		}

		Customer customer = customerRepository.findById(customerId)
			.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

		customerRepository.softDeleteById(customerId, "customer");
	}

	private void validateUpdateRequest(Customer customer, CustomerProfileUpdateRequestDto request) {
		if (request.getEmail() != null) {
					if (!EMAIL_PATTERN.matcher(request.getEmail()).matches()) {
			throw new BusinessException(CustomerErrorCode.INVALID_EMAIL_FORMAT);
		}
		if (!request.getEmail().equals(customer.getEmail()) &&
			customerRepository.existsByEmailAndDeletedAtIsNull(request.getEmail())) {
			throw new BusinessException(CustomerErrorCode.EMAIL_ALREADY_EXISTS);
		}
		}

		if (request.getNickname() != null &&
			!request.getNickname().equals(customer.getNickname()) &&
			customerRepository.existsByNicknameAndDeletedAtIsNull(request.getNickname())) {
			throw new BusinessException(CustomerErrorCode.NICKNAME_ALREADY_EXISTS);
		}

		if (request.getPhoneNumber() != null &&
			!PHONE_PATTERN.matcher(request.getPhoneNumber()).matches()) {
			throw new BusinessException(CustomerErrorCode.INVALID_PHONE_FORMAT);
		}
	}

	private void applyProfileUpdates(Customer customer, CustomerProfileUpdateRequestDto request) {
		Optional.ofNullable(request.getNickname())
			.ifPresent(customer::setNickname);
		Optional.ofNullable(request.getEmail())
			.ifPresent(customer::setEmail);
		Optional.ofNullable(request.getPhoneNumber())
			.ifPresent(customer::setPhoneNumber);
	}

	public UserDto findByEmail(String email) {
		Customer customer = customerRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Customer not found"));

		return UserDto.builder()
				.id(customer.getId())
				.email(customer.getEmail())
				.password(customer.getPassword())
				.name(customer.getName())
				.role("customer")
				.build();
	}

	@Transactional
	public void signup(SignupRequestDto request) {
		log.info("=== 회원가입 시작 ===");
		log.info("요청 데이터: email={}, name={}, nickname={}, phone={}, role={}", 
			request.getEmail(), request.getName(), request.getNickname(), request.getPhone(), request.getRole());
		
		try {
			Customer customer = new Customer();
			customer.setEmail(request.getEmail());
			customer.setPassword(request.getPassword());
			customer.setName(request.getName());
			customer.setNickname(request.getNickname());
			customer.setPhoneNumber(request.getPhone());
			
			// 회원가입 시 포인트는 기본값 0으로 설정
			customer.setPoints(0);
			
			log.info("Customer 엔티티 생성 완료: {}", customer);
			
			Customer savedCustomer = customerRepository.save(customer);
			log.info("데이터베이스 저장 완료: savedCustomer={}", savedCustomer);
			
			// 저장된 데이터 재확인
			Customer foundCustomer = customerRepository.findById(savedCustomer.getId()).orElse(null);
			log.info("저장된 데이터 재확인: foundCustomer={}", foundCustomer);
			
			log.info("=== 회원가입 완료 ===");
		} catch (Exception e) {
			log.error("=== 회원가입 실패 ===");
			log.error("오류 발생: {}", e.getMessage(), e);
			throw new RuntimeException("회원가입 처리 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	@Transactional
	public void changePassword(UUID customerId, ChangePasswordRequestDto request) {
		Customer customer = customerRepository.findById(customerId)
			.orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

		if (!passwordEncoder.matches(request.getCurrentPassword(), customer.getPassword())) {
			throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
		}

		customer.setPassword(passwordEncoder.encode(request.getNewPassword()));
		customerRepository.save(customer);
	}

	/**
	 * 포인트 충전
	 */
	@Transactional
	public ChargePointsResponseDto chargePoints(UUID customerId, ChargePointsRequestDto request) {
		log.info("포인트 충전 시작: customerId={}, points={}", customerId, request.getPoints());

		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

		// 포인트 충전 유효성 검증
		if (request.getPoints() == null || request.getPoints() <= 0) {
			throw new IllegalArgumentException("충전할 포인트는 0보다 커야 합니다.");
		}

		// 최대 포인트 제한 (예: 1,000,000 포인트)
		Integer maxPoints = 1000000;
		if (customer.getPoints() + request.getPoints() > maxPoints) {
			throw new IllegalStateException(String.format(
				"포인트 충전 한도를 초과했습니다. 현재: %d, 충전요청: %d, 최대: %d", 
				customer.getPoints(), request.getPoints(), maxPoints));
		}

		// 포인트 충전 실행
		Integer currentPoints = customer.getPoints();
		customer.setPoints(currentPoints + request.getPoints());
		customerRepository.save(customer);

		log.info("포인트 충전 완료: customerId={}, chargedPoints={}, totalPoints={}", 
				customerId, request.getPoints(), customer.getPoints());

		return ChargePointsResponseDto.builder()
				.customerId(customerId)
				.chargedPoints(request.getPoints())
				.totalPoints(customer.getPoints())
				.availablePoints(customer.getPoints() - customer.getReservedPoints())
				.chargedAt(customer.getUpdatedAt())
				.message("포인트가 성공적으로 충전되었습니다.")
				.build();
	}

	/**
	 * 포인트 예약 (실제 차감하지 않고 예약만)
	 */
	@Transactional
	public void reservePoints(UUID customerId, Integer pointsToReserve) {
		log.info("포인트 예약 시작: customerId={}, points={}", customerId, pointsToReserve);

		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

		if (pointsToReserve == null || pointsToReserve <= 0) {
			throw new IllegalArgumentException("예약할 포인트는 0보다 커야 합니다.");
		}
		
		// 사용 가능한 포인트 = 보유 포인트 - 이미 예약된 포인트
		Integer availablePoints = customer.getPoints() - customer.getReservedPoints();
		if (availablePoints < pointsToReserve) {
			throw new IllegalStateException(String.format(
				"사용 가능한 포인트가 부족합니다. 보유: %d, 예약됨: %d, 사용가능: %d, 필요: %d", 
				customer.getPoints(), customer.getReservedPoints(), availablePoints, pointsToReserve));
		}
		
		// 예약 포인트만 증가, 실제 포인트는 차감하지 않음
		customer.setReservedPoints(customer.getReservedPoints() + pointsToReserve);
		customerRepository.save(customer);

		log.info("포인트 예약 완료: customerId={}, reservedPoints={}", customerId, customer.getReservedPoints());
	}

	/**
	 * 예약된 포인트를 실제로 차감 (결제 완료 시)
	 */
	@Transactional
	public void processReservedPoints(UUID customerId, Integer pointsToProcess) {
		log.info("예약된 포인트 처리 시작: customerId={}, points={}", customerId, pointsToProcess);

		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

		if (pointsToProcess == null || pointsToProcess <= 0) {
			throw new IllegalArgumentException("처리할 포인트는 0보다 커야 합니다.");
		}
		
		if (customer.getReservedPoints() < pointsToProcess) {
			throw new IllegalStateException(String.format(
				"예약된 포인트가 부족합니다. 예약됨: %d, 처리요청: %d", 
				customer.getReservedPoints(), pointsToProcess));
		}
		
		// 예약된 포인트에서 차감하고, 실제 포인트에서도 차감
		customer.setReservedPoints(customer.getReservedPoints() - pointsToProcess);
		customer.setPoints(customer.getPoints() - pointsToProcess);
		customerRepository.save(customer);

		log.info("예약된 포인트 처리 완료: customerId={}, remainingReserved={}, remainingPoints={}", 
				customerId, customer.getReservedPoints(), customer.getPoints());
	}

	/**
	 * 포인트 예약 취소 (주문 취소 시)
	 */
	@Transactional
	public void cancelReservedPoints(UUID customerId, Integer pointsToCancel) {
		log.info("포인트 예약 취소 시작: customerId={}, points={}", customerId, pointsToCancel);

		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

		if (pointsToCancel == null || pointsToCancel <= 0) {
			throw new IllegalArgumentException("취소할 포인트는 0보다 커야 합니다.");
		}
		
		if (customer.getReservedPoints() < pointsToCancel) {
			throw new IllegalStateException(String.format(
				"취소할 예약 포인트가 부족합니다. 예약됨: %d, 취소요청: %d", 
				customer.getReservedPoints(), pointsToCancel));
		}
		
		// 예약된 포인트만 감소
		customer.setReservedPoints(customer.getReservedPoints() - pointsToCancel);
		customerRepository.save(customer);

		log.info("포인트 예약 취소 완료: customerId={}, remainingReserved={}", 
				customerId, customer.getReservedPoints());
	}

	/**
	 * 포인트 추가 (적립 등)
	 */
	@Transactional
	public void addPoints(UUID customerId, Integer pointsToAdd) {
		log.info("포인트 추가 시작: customerId={}, points={}", customerId, pointsToAdd);

		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));

		if (pointsToAdd == null || pointsToAdd <= 0) {
			throw new IllegalArgumentException("추가할 포인트는 0보다 커야 합니다.");
		}
		
		customer.setPoints(customer.getPoints() + pointsToAdd);
		customerRepository.save(customer);

		log.info("포인트 추가 완료: customerId={}, totalPoints={}", customerId, customer.getPoints());
	}

	/**
	 * 사용 가능한 포인트 조회
	 */
	public Integer getAvailablePoints(UUID customerId) {
		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
		
		return customer.getPoints() - customer.getReservedPoints();
	}

	/**
	 * 총 보유 포인트 조회
	 */
	public Integer getTotalPoints(UUID customerId) {
		Customer customer = customerRepository.findById(customerId)
				.orElseThrow(() -> new BusinessException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
		
		return customer.getPoints();
	}
}
