package com.eatcloud.orderservice.controller;

import com.eatcloud.orderservice.dto.request.CreateOrderRequest;
import com.eatcloud.orderservice.dto.request.PaymentCompleteRequest;
import com.eatcloud.orderservice.dto.response.CreateOrderResponse;
import com.eatcloud.orderservice.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController 현대적 단위 테스트")
class ModernOrderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private ObjectMapper objectMapper;
    private UUID customerId;
    private UUID orderId;
    private UUID storeId;
    private CreateOrderRequest createOrderRequest;
    private CreateOrderResponse createOrderResponse;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(orderController)
                .setControllerAdvice(new TestExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();

        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        storeId = UUID.randomUUID();

        createOrderRequest = CreateOrderRequest.builder()
                .storeId(storeId)
                .orderType("DELIVERY")
                .usePoints(false)
                .pointsToUse(0)
                .build();

        createOrderResponse = CreateOrderResponse.builder()
                .orderId(orderId)
                .orderNumber("ORD-20241215-ABCDE")
                .totalPrice(25000)
                .finalPaymentAmount(25000)
                .orderStatus("PENDING")
                .message("주문이 생성되었습니다.")
                .build();
    }

    @Test
    @DisplayName("장바구니에서 주문 생성 - 성공")
    void createOrderFromCart_Success() throws Exception {
        // Given
        given(orderService.createOrderFromCart(eq(customerId), any(CreateOrderRequest.class)))
                .willReturn(createOrderResponse);

        // When & Then
        mockMvc.perform(post("/orders/customers/{customerId}/from-cart", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.orderNumber").value("ORD-20241215-ABCDE"))
                .andExpect(jsonPath("$.totalPrice").value(25000))
                .andExpect(jsonPath("$.message").value("주문이 생성되었습니다."));

        verify(orderService).createOrderFromCart(eq(customerId), any(CreateOrderRequest.class));
    }

    @Test
    @DisplayName("결제 완료 처리 - 성공")
    void completePayment_Success() throws Exception {
        // Given
        UUID paymentId = UUID.randomUUID();
        PaymentCompleteRequest paymentRequest = PaymentCompleteRequest.builder()
                .paymentId(paymentId)
                .build();

        mockMvc.perform(post("/orders/{orderId}/payment/complete", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(paymentRequest)))
                .andExpect(status().isOk());

        verify(orderService).completePayment(orderId, paymentId);
    }

    @Test
    @DisplayName("잘못된 JSON 형식 - 400 에러")
    void invalidJsonFormat_BadRequest() throws Exception {
        // When & Then
        mockMvc.perform(post("/orders/customers/{customerId}/from-cart", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("서비스 예외 처리 - 500 에러")
    void serviceException_InternalServerError() throws Exception {
        // Given
        given(orderService.createOrderFromCart(eq(customerId), any(CreateOrderRequest.class)))
                .willThrow(new RuntimeException("데이터베이스 연결 오류"));

        // When & Then
        mockMvc.perform(post("/orders/customers/{customerId}/from-cart", customerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createOrderRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("데이터베이스 연결 오류"));
    }

    @RestControllerAdvice
    static class TestExceptionHandler {

        @ExceptionHandler(RuntimeException.class)
        public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse(false, e.getMessage()));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(false, e.getMessage()));
        }
    }

    static class ErrorResponse {
        private boolean success;
        private String message;

        public ErrorResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
