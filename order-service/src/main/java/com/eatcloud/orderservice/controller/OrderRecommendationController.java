package com.eatcloud.orderservice.controller;

import com.eatcloud.logging.annotation.ExceptionHandling;
import com.eatcloud.logging.annotation.Loggable;
import com.eatcloud.logging.recommendation.RecommendationEventLogger;
import com.eatcloud.orderservice.dto.request.CreateOrderRequest;
import com.eatcloud.orderservice.dto.response.ApiResponse;
import com.eatcloud.orderservice.dto.response.CreateOrderResponse;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 주문 생성과 추천 이벤트 로깅을 담당하는 Controller
 * 
 * 이 Controller는 다음 추천 이벤트를 로깅합니다:
 * - ORDER_COMPLETE: 주문 완료
 */
@RestController
@RequestMapping("/api/v1/orders/recommendation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "4-2. OrderRecommendationController", description = "주문 및 추천 이벤트 API")
@Loggable(level = Loggable.LogLevel.INFO,