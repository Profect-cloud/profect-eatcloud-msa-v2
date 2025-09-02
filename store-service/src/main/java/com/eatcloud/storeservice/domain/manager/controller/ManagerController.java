package com.eatcloud.storeservice.domain.manager.controller;

import com.eatcloud.storeservice.domain.manager.message.ManagerResponseMessage;
import com.eatcloud.storeservice.domain.manager.service.ManagerService;
import com.eatcloud.storeservice.domain.menu.dto.MenuRequestDto;
import com.eatcloud.storeservice.domain.menu.dto.MenuResponseDto;
import com.eatcloud.storeservice.domain.menu.dto.MenuUpdateRequestDto;
import com.eatcloud.storeservice.domain.menu.entity.Menu;
import com.eatcloud.storeservice.domain.store.dto.AiDescriptionRequestDto;
import com.eatcloud.storeservice.domain.store.dto.AiDescriptionResponseDto;
import com.eatcloud.storeservice.domain.store.dto.StoreCreateRequestDto;
import com.eatcloud.storeservice.domain.store.dto.StoreUpdateRequestDto;
import com.eatcloud.storeservice.domain.store.service.AiDescriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.eatcloud.autoresponse.core.ApiResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER')")
@Tag(name = "5. ManagerController")
public class ManagerController {

    private final ManagerService managerService;
    private final AiDescriptionService aiDescriptionService;

    @Operation(summary = "1-1. 메뉴 생성")
    @PostMapping("/stores/{storeId}/menus")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<MenuResponseDto> createMenu(@PathVariable UUID storeId, @RequestBody @Valid MenuRequestDto dto) {
        Menu created = managerService.createMenu(storeId, dto);
        return ApiResponse.success(MenuResponseDto.from(created));
    }

    @Operation(summary = "1-2. 메뉴 수정")
    @PutMapping("/stores/{storeId}/menus/{menuId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<MenuResponseDto> updateMenu(
            @PathVariable UUID storeId,
            @PathVariable UUID menuId,
            @RequestBody @Valid MenuUpdateRequestDto dto
    ) {
        Menu updated = managerService.updateMenu(storeId, menuId, dto);
        return ApiResponse.success(MenuResponseDto.from(updated));
    }

    @Operation(summary = "1-3. 메뉴 삭제")
    @DeleteMapping("/stores/{storeId}/menus/{menuId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<ManagerResponseMessage> deleteMenu(@PathVariable UUID menuId) {
        managerService.deleteMenu(menuId);
        return ApiResponse.success(ManagerResponseMessage.MENU_DELETE_SUCCESS);
    }

    @Operation(summary = "1-4. AI 메뉴 설명 생성")
    @PostMapping("/stores/{storeId}/menus/ai-description")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<AiDescriptionResponseDto> generateAIDescription(
            @PathVariable UUID storeId,
            @RequestBody @Valid AiDescriptionRequestDto requestDto) {

        String description = aiDescriptionService.generateDescription(requestDto);
        return ApiResponse.success(new AiDescriptionResponseDto(description));
    }

    @Operation(summary = "2-1. 가게 정보 수정")
    @PutMapping("/stores/{storeId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<ManagerResponseMessage> updateStore(@PathVariable UUID storeId,
                                                           @RequestBody @Valid StoreUpdateRequestDto dto) {
        managerService.updateStore(storeId, dto);
        return ApiResponse.success(ManagerResponseMessage.STORE_UPDATE_SUCCESS);
    }

    // 가게 생성
    @Operation(summary = "2-2. 가게 생성")
    @PostMapping("/stores")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<ManagerResponseMessage> createStore(@PathVariable UUID storeId,
                                                           @RequestBody @Valid StoreCreateRequestDto dto) {
        managerService.createStore(storeId, dto);
        return ApiResponse.success(ManagerResponseMessage.STORE_REGISTRATION_SUCCESS);
    }
    // 가게 삭제
    @Operation(summary = "2-3. 가게 삭제")
    @DeleteMapping("/stores/{storeId}")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<ManagerResponseMessage> deleteStore(@PathVariable UUID storeId
                                                           ) {
        managerService.deleteStore(storeId);
        return ApiResponse.success(ManagerResponseMessage.STORE_CLOSURE_SUCCESS);
    }

}
