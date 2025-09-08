package com.eatcloud.customerservice.controller;

import java.util.UUID;

import com.eatcloud.customerservice.dto.SignupRequestDto;
import com.eatcloud.customerservice.dto.UserDto;
import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.logging.mdc.MDCUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import com.eatcloud.customerservice.dto.request.ChargePointsRequestDto;
import com.eatcloud.customerservice.dto.request.ChangePasswordRequestDto;
import com.eatcloud.customerservice.dto.request.CustomerProfileUpdateRequestDto;
import com.eatcloud.customerservice.dto.request.CustomerWithdrawRequestDto;
import com.eatcloud.customerservice.dto.response.ChargePointsResponseDto;
import com.eatcloud.customerservice.dto.response.CustomerProfileResponseDto;
import com.eatcloud.customerservice.error.CustomerErrorCode;
import com.eatcloud.autoresponse.error.BusinessException;
import com.eatcloud.customerservice.message.ResponseMessage;
import com.eatcloud.customerservice.service.CustomerService;

@Slf4j
@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "3-1. CustomerController", description = "고객 프로필 관리 API")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	private UUID getCustomerUuid(@AuthenticationPrincipal Jwt jwt) {
		try {
			UUID customerId = UUID.fromString(jwt.getSubject());
			// JWT에서 추출한 사용자 정보를 MDC에 설정
			MDCUtil.setUserId(customerId.toString());
			MDCUtil.setUserRole("customer");
			log.debug("Customer authenticated: {}", customerId);
			return customerId;
		} catch (IllegalArgumentException e) {
			log.error("Invalid customer ID in JWT: {}", jwt.getSubject());
			throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_ID);
		}
	}

	@Operation(summary = "1. 고객 프로필 조회", description = "인증된 고객의 프로필 정보를 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 고객 ID"),
		@ApiResponse(responseCode = "401", description = "인증 실패"),
		@ApiResponse(responseCode = "404", description = "고객을 찾을 수 없음")
	})
	@GetMapping("/profile")
	@ResponseStatus(HttpStatus.OK)
	@Loggable
	public com.eatcloud.autoresponse.core.ApiResponse<CustomerProfileResponseDto> getCustomer(
		@AuthenticationPrincipal Jwt jwt) {

		UUID customerId = getCustomerUuid(jwt);
		log.info("Customer profile request for customer: {}", customerId);
		
		CustomerProfileResponseDto response = customerService.getCustomerProfile(customerId);
		log.info("Customer profile retrieved successfully for customer: {}", customerId);
		return com.eatcloud.autoresponse.core.ApiResponse.success(response);
	}

	@Operation(summary = "2. 고객 프로필 수정", description = "인증된 고객의 프로필 정보를 수정합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
		@ApiResponse(responseCode = "401", description = "인증 실패"),
		@ApiResponse(responseCode = "404", description = "고객을 찾을 수 없음"),
		@ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복")
	})
	@PatchMapping("/profile")
	@ResponseStatus(HttpStatus.OK)
	@Loggable(maskSensitiveData = true)
	public com.eatcloud.autoresponse.core.ApiResponse<ResponseMessage> updateCustomer(
		@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody CustomerProfileUpdateRequestDto request) {

		UUID customerId = getCustomerUuid(jwt);
		log.info("Customer profile update request for customer: {}", customerId);
		
		customerService.updateCustomer(customerId, request);
		log.info("Customer profile updated successfully for customer: {}", customerId);
		return com.eatcloud.autoresponse.core.ApiResponse.success(ResponseMessage.PROFILE_UPDATE_SUCCESS);
	}

	@Operation(summary = "3. 고객 탈퇴", description = "인증된 고객이 서비스에서 탈퇴합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "탈퇴 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 데이터 또는 탈퇴 사유 누락"),
		@ApiResponse(responseCode = "401", description = "인증 실패"),
		@ApiResponse(responseCode = "404", description = "고객을 찾을 수 없음")
	})
	@PostMapping("/withdraw")
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<ResponseMessage> withdrawCustomer(
		@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody CustomerWithdrawRequestDto request) {

		UUID customerId = getCustomerUuid(jwt);
		customerService.withdrawCustomer(customerId, request);
		return com.eatcloud.autoresponse.core.ApiResponse.success(ResponseMessage.CUSTOMER_WITHDRAW_SUCCESS);
	}

	@Operation(summary = "4. 고객 포인트 조회", description = "고객의 현재 포인트를 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공"),
		@ApiResponse(responseCode = "404", description = "고객을 찾을 수 없음")
	})
	@GetMapping("/{customerId}/points")
	@ResponseStatus(HttpStatus.OK)
	public ResponseEntity<Integer> getCustomerPoints(@PathVariable UUID customerId) {
		Integer points = customerService.getCustomerPoints(customerId);
		return ResponseEntity.ok(points);
	}

	@GetMapping("/search")
	public ResponseEntity<UserDto> searchByEmail(@RequestParam String email) {
		UserDto userDto = customerService.findByEmail(email);
		return ResponseEntity.ok(userDto);
	}

	@Operation(summary = "5. 고객 회원가입", description = "새로운 고객을 등록합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "회원가입 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
		@ApiResponse(responseCode = "409", description = "이메일 또는 닉네임 중복")
	})
	@PostMapping("/signup")
	public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequestDto request) {
		customerService.signup(request);
		return ResponseEntity.ok().build();
	}

	@PutMapping("/change-password")
	public ResponseEntity<String> changePassword(
		@AuthenticationPrincipal Jwt jwt,
		@RequestBody ChangePasswordRequestDto request) {
		UUID customerId = getCustomerUuid(jwt);
		customerService.changePassword(customerId, request);
		return ResponseEntity.ok("비밀번호가 변경되었습니다.");
	}

	@Operation(summary = "고객 ID로 조회", description = "고객 ID로 고객 정보를 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공"),
		@ApiResponse(responseCode = "404", description = "고객을 찾을 수 없음")
	})
	@GetMapping("/{customerId}")
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<CustomerProfileResponseDto> getCustomerById(
		@PathVariable UUID customerId) {

		CustomerProfileResponseDto response = customerService.getCustomerProfile(customerId);
		return com.eatcloud.autoresponse.core.ApiResponse.success(response);
	}

	@Operation(summary = "포인트 충전", description = "고객의 포인트를 충전합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "충전 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
		@ApiResponse(responseCode = "404", description = "고객을 찾을 수 없음")
	})
	@PostMapping("/{customerId}/points/charge")
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<ChargePointsResponseDto> chargePoints(
		@PathVariable UUID customerId,
		@Valid @RequestBody ChargePointsRequestDto request) {

		ChargePointsResponseDto response = customerService.chargePoints(customerId, request);
		return com.eatcloud.autoresponse.core.ApiResponse.success(response);
	}

	@Operation(summary = "내 포인트 충전", description = "현재 로그인한 사용자의 포인트를 충전합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "충전 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
		@ApiResponse(responseCode = "404", description = "고객을 찾을 수 없음")
	})
	@PostMapping("/me/points/charge")
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<ChargePointsResponseDto> chargeMyPoints(
		@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody ChargePointsRequestDto request) {

		UUID customerId = UUID.fromString(jwt.getSubject());
		ChargePointsResponseDto response = customerService.chargePoints(customerId, request);
		return com.eatcloud.autoresponse.core.ApiResponse.success(response);
	}

}