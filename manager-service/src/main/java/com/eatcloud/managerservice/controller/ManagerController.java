package com.eatcloud.managerservice.controller;

import java.util.UUID;

import com.eatcloud.autoresponse.core.ApiResponse;
import com.eatcloud.managerservice.dto.SignupRequestDto;
import com.eatcloud.managerservice.dto.ManagerDto;
import com.eatcloud.managerservice.dto.UserDto;
import com.eatcloud.managerservice.service.ManagerService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/managers")
@RequiredArgsConstructor
public class ManagerController {

	private final ManagerService managerService;

	@GetMapping("/{id}")
	public ResponseEntity<ApiResponse<ManagerDto>> get(@PathVariable UUID id) {
		ManagerDto dto = managerService.getOrThrow(id); // 못 찾으면 BusinessException 던지기(아래 4번)
		return ResponseEntity.ok(ApiResponse.ok("조회 성공", dto));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
		managerService.remove(id);
		return ResponseEntity.ok(ApiResponse.success());
	}

	@PostMapping("/signup")
	public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequestDto request) {
		managerService.signup(request);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/search")
	public ResponseEntity<UserDto> searchByEmail(@RequestParam String email) {
		UserDto userDto = managerService.findByEmail(email);
		return ResponseEntity.ok(userDto);
	}
}

