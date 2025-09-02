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

import com.eatcloud.customerservice.dto.request.ChangePasswordRequestDto;
import com.eatcloud.customerservice.dto.request.CustomerProfileUpdateRequestDto;
import com.eatcloud.customerservice.dto.request.CustomerWithdrawRequestDto;
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
}
