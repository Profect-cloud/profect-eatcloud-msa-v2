package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.dto.CartItem;
import com.eatcloud.orderservice.dto.request.AddCartItemRequest;
import com.eatcloud.orderservice.dto.request.UpdateCartItemRequest;
import com.eatcloud.orderservice.exception.CartException;
import com.eatcloud.orderservice.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService 단위 테스트")
class CartServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private CartService cartService;

    private UUID customerId;
    private UUID storeId;
    private UUID menuId;
    private AddCartItemRequest addRequest;
    private UpdateCartItemRequest updateRequest;
    private List<CartItem> cartItems;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        menuId = UUID.randomUUID();


        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        addRequest = AddCartItemRequest.builder()
                .storeId(storeId)
                .menuId(menuId)
                .menuName("김치찌개")
                .quantity(2)
                .price(8000)
                .build();

        updateRequest = UpdateCartItemRequest.builder()
                .menuId(menuId)
                .quantity(3)
                .build();

        cartItems = Collections.singletonList(
                CartItem.builder()
                        .storeId(storeId)
                        .menuId(menuId)
                        .menuName("김치찌개")
                        .quantity(1)
                        .price(8000)
                        .build()
        );
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 신규 아이템 성공")
    void addItem_NewItem_Success() {

        UUID newMenuId = UUID.randomUUID();
        AddCartItemRequest newItemRequest = AddCartItemRequest.builder()
                .storeId(storeId)
                .menuId(newMenuId)
                .menuName("된장찌개")
                .quantity(1)
                .price(7000)
                .build();

        given(valueOperations.get(anyString())).willReturn(cartItems);


        assertThatNoException().isThrownBy(() -> 
            cartService.addItem(customerId, newItemRequest)
        );

        verify(redisTemplate).opsForValue();
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 기존 아이템 수량 증가")
    void addItem_ExistingItem_IncreaseQuantity() {
        given(valueOperations.get(anyString())).willReturn(cartItems);

        assertThatNoException().isThrownBy(() ->
            cartService.addItem(customerId, addRequest)
        );

        verify(redisTemplate).opsForValue();
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 다른 매장 상품 예외")
    void addItem_DifferentStore_ThrowsException() {
        UUID differentStoreId = UUID.randomUUID();
        AddCartItemRequest differentStoreRequest = AddCartItemRequest.builder()
                .storeId(differentStoreId)
                .menuId(UUID.randomUUID())
                .menuName("다른매장메뉴")
                .quantity(1)
                .price(5000)
                .build();

        given(valueOperations.get(anyString())).willReturn(cartItems);

        assertThatThrownBy(() -> cartService.addItem(customerId, differentStoreRequest))
                .isInstanceOf(CartException.class);
    }

    @Test
    @DisplayName("장바구니 아이템 추가 - 잘못된 수량 예외")
    void addItem_InvalidQuantity_ThrowsException() {
        AddCartItemRequest invalidRequest = AddCartItemRequest.builder()
                .storeId(storeId)
                .menuId(menuId)
                .menuName("김치찌개")
                .quantity(0)
                .price(8000)
                .build();


        assertThatThrownBy(() -> cartService.addItem(customerId, invalidRequest))
                .isInstanceOf(CartException.class);
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 성공")
    void updateItemQuantity_Success() {
        given(valueOperations.get(anyString())).willReturn(cartItems);

        assertThatNoException().isThrownBy(() ->
            cartService.updateItemQuantity(customerId, updateRequest)
        );

        verify(redisTemplate).opsForValue();
    }

    @Test
    @DisplayName("장바구니 아이템 수량 수정 - 존재하지 않는 아이템 예외")
    void updateItemQuantity_ItemNotFound_ThrowsException() {
        UUID nonExistentMenuId = UUID.randomUUID();
        UpdateCartItemRequest notFoundRequest = UpdateCartItemRequest.builder()
                .menuId(nonExistentMenuId)
                .quantity(5)
                .build();

        given(valueOperations.get(anyString())).willReturn(cartItems);

        assertThatThrownBy(() -> cartService.updateItemQuantity(customerId, notFoundRequest))
                .isInstanceOf(CartException.class);
    }

    @Test
    @DisplayName("장바구니 아이템 삭제 - 성공")
    void removeItem_Success() {
        given(valueOperations.get(anyString())).willReturn(cartItems);

        assertThatNoException().isThrownBy(() ->
            cartService.removeItem(customerId, menuId)
        );

        verify(redisTemplate).opsForValue();
    }

    @Test
    @DisplayName("장바구니 아이템 삭제 - 존재하지 않는 아이템 예외")
    void removeItem_ItemNotFound_ThrowsException() {
        UUID nonExistentMenuId = UUID.randomUUID();
        given(valueOperations.get(anyString())).willReturn(cartItems);
        assertThatThrownBy(() -> cartService.removeItem(customerId, nonExistentMenuId))
                .isInstanceOf(CartException.class);
    }

    @Test
    @DisplayName("장바구니 조회 - 성공")
    void getCart_Success() {
        given(valueOperations.get(anyString())).willReturn(cartItems);

        List<CartItem> result = cartService.getCart(customerId);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getMenuId()).isEqualTo(menuId);
        assertThat(result.getFirst().getMenuName()).isEqualTo("김치찌개");
    }

    @Test
    @DisplayName("장바구니 조회 - 빈 장바구니")
    void getCart_EmptyCart() {
        given(valueOperations.get(anyString())).willReturn(null);

        List<CartItem> result = cartService.getCart(customerId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("장바구니 전체 삭제 - 성공")
    void clearCart_Success() {

        assertThatNoException().isThrownBy(() ->
            cartService.clearCart(customerId)
        );

        verify(cartRepository).deleteByCustomerId(customerId);
    }

    @Test
    @DisplayName("고객 ID 유효성 검사 - null 값 예외")
    void validateCustomerId_NullValue_ThrowsException() {
        assertThatThrownBy(() -> cartService.addItem(null, addRequest))
                .isInstanceOf(CartException.class);
    }

    @Test
    @DisplayName("수량을 0으로 수정 시 아이템 제거")
    void updateItemQuantity_ZeroQuantity_RemovesItem() {

        UpdateCartItemRequest zeroQuantityRequest = UpdateCartItemRequest.builder()
                .menuId(menuId)
                .quantity(0)
                .build();

        given(valueOperations.get(anyString())).willReturn(cartItems);


        assertThatNoException().isThrownBy(() ->
            cartService.updateItemQuantity(customerId, zeroQuantityRequest)
        );


        verify(redisTemplate).opsForValue();
    }

    @Test
    @DisplayName("주문 후 장바구니 무효화 - 성공")
    void invalidateCartAfterOrder_Success() {

        assertThatNoException().isThrownBy(() ->
            cartService.invalidateCartAfterOrder(customerId)
        );
        verify(cartRepository).deleteByCustomerId(customerId);
    }
}
