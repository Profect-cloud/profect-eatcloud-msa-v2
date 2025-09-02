package com.eatcloud.customerservice.controller;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.eatcloud.customerservice.dto.request.AddressRequestDto;
import com.eatcloud.customerservice.dto.response.AddressResponseDto;
import com.eatcloud.customerservice.error.CustomerErrorCode;
import com.eatcloud.autoresponse.error.BusinessException;
import com.eatcloud.customerservice.message.ResponseMessage;
import com.eatcloud.customerservice.service.AddressService;

@RestController
@RequestMapping("/api/v1/customers/addresses")
@Tag(name = "3-2. AddressController", description = "배송지 관리 API")
public class AddressController {

	private final AddressService addressService;

	public AddressController(AddressService addressService) {
		this.addressService = addressService;
	}

	private UUID getCustomerUuid(@AuthenticationPrincipal Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(CustomerErrorCode.INVALID_CUSTOMER_ID);
		}
	}

	@Operation(summary = "1. 배송지 목록 조회", description = "사용자의 모든 배송지를 조회합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "조회 성공"),
		@ApiResponse(responseCode = "401", description = "인증 실패")
	})
	@GetMapping
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<List<AddressResponseDto>> getAddressList(
		@AuthenticationPrincipal Jwt jwt) {
		UUID customerId = getCustomerUuid(jwt);
		List<AddressResponseDto> addresses = addressService.getAddressList(customerId);
		return com.eatcloud.autoresponse.core.ApiResponse.success(addresses);
	}

	@Operation(summary = "2. 배송지 등록", description = "새로운 배송지를 등록합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "등록 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
		@ApiResponse(responseCode = "401", description = "인증 실패")
	})
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public com.eatcloud.autoresponse.core.ApiResponse<List<AddressResponseDto>> createAddress(
		@AuthenticationPrincipal Jwt jwt,
		@Valid @RequestBody AddressRequestDto request) {
		UUID customerId = getCustomerUuid(jwt);
		AddressResponseDto response = addressService.createAddress(customerId, request);
		return com.eatcloud.autoresponse.core.ApiResponse.created(Collections.singletonList(response));
	}

	@Operation(summary = "3. 배송지 수정", description = "기존 배송지 정보를 수정합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "수정 성공"),
		@ApiResponse(responseCode = "400", description = "잘못된 요청 데이터"),
		@ApiResponse(responseCode = "401", description = "인증 실패"),
		@ApiResponse(responseCode = "404", description = "배송지를 찾을 수 없음")
	})
	@PutMapping("/{addressId}")
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<List<AddressResponseDto>> updateAddress(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID addressId,
		@Valid @RequestBody AddressRequestDto request) {
		UUID customerId = getCustomerUuid(jwt);
		AddressResponseDto response = addressService.updateAddress(customerId, addressId, request);
		return com.eatcloud.autoresponse.core.ApiResponse.success(Collections.singletonList(response));
	}

	@Operation(summary = "4. 배송지 삭제", description = "배송지를 삭제합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "삭제 성공"),
		@ApiResponse(responseCode = "401", description = "인증 실패"),
		@ApiResponse(responseCode = "404", description = "배송지를 찾을 수 없음")
	})
	@DeleteMapping("/{addressId}")
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<ResponseMessage> deleteAddress(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID addressId) {
		UUID customerId = getCustomerUuid(jwt);
		addressService.deleteAddress(customerId, addressId);
		return com.eatcloud.autoresponse.core.ApiResponse.success(ResponseMessage.ADDRESS_DELETE_SUCCESS);
	}

	@Operation(summary = "5. 기본 배송지 설정", description = "선택한 배송지를 기본 배송지로 설정합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "204", description = "설정 성공"),
		@ApiResponse(responseCode = "401", description = "인증 실패"),
		@ApiResponse(responseCode = "404", description = "배송지를 찾을 수 없음")
	})
	@PutMapping("/{addressId}/select")
	@ResponseStatus(HttpStatus.OK)
	public com.eatcloud.autoresponse.core.ApiResponse<ResponseMessage> setDefaultAddress(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID addressId) {
		UUID customerId = getCustomerUuid(jwt);
		addressService.setDefaultAddress(customerId, addressId);
		return com.eatcloud.autoresponse.core.ApiResponse.success(ResponseMessage.ADDRESS_SELECT_SUCCESS);
	}

}