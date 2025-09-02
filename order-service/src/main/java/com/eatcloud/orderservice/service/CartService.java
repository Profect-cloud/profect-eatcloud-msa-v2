package com.eatcloud.orderservice.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.eatcloud.orderservice.dto.CartItem;
import com.eatcloud.orderservice.dto.request.AddCartItemRequest;
import com.eatcloud.orderservice.dto.request.UpdateCartItemRequest;
import com.eatcloud.orderservice.entity.Cart;
import com.eatcloud.orderservice.repository.CartRepository;
import com.eatcloud.orderservice.exception.CartException;
import com.eatcloud.orderservice.exception.ErrorCode;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CartRepository cartRepository;
    private final PlatformTransactionManager transactionManager;

    private static final String CART_KEY_PREFIX = "cart:";
    private static final Duration CART_TTL = Duration.ofHours(1);

    private final Map<UUID, List<CartItem>> pendingChanges = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final int BATCH_INTERVAL_SECONDS = 10;


    public void addItem(UUID customerId, AddCartItemRequest request) {
        validateCustomerId(customerId);
        validateAddItemRequest(request);

        try {
            List<CartItem> cartItems = getCartFromRedis(customerId);

            if (cartItems.isEmpty()) {
                cartItems = new ArrayList<>();
            }

            validateStoreConsistency(cartItems, request.getStoreId());

            Optional<CartItem> existingItem = cartItems.stream()
                .filter(item -> item.getMenuId().equals(request.getMenuId()))
                .findFirst();

            if (existingItem.isPresent()) {
                CartItem item = existingItem.get();
                item.setQuantity(item.getQuantity() + request.getQuantity());
                log.debug("Updated existing cart item: customerId={}, menuId={}, newQuantity={}",
                    customerId, request.getMenuId(), item.getQuantity());
            } else {
                CartItem newItem = CartItem.builder()
                    .menuId(request.getMenuId())
                    .menuName(request.getMenuName())
                    .quantity(request.getQuantity())
                    .price(request.getPrice())
                    .storeId(request.getStoreId())
                    .build();
                cartItems.add(newItem);
                log.debug("Added new cart item: customerId={}, menuId={}, quantity={}",
                    customerId, request.getMenuId(), request.getQuantity());
            }

            saveCartToRedis(customerId, cartItems);
            syncToDatabaseAsync(customerId, cartItems);

            log.info("Successfully added item to cart: customerId={}, menuId={}",
                customerId, request.getMenuId());

        } catch (CartException e) {
            log.warn("Cart operation failed: customerId={}, error={}", customerId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to add item to cart for customer: {}", customerId, e);
            throw new CartException(ErrorCode.CART_NOT_FOUND);
        }
    }

    public List<CartItem> getCart(UUID customerId) {
        validateCustomerId(customerId);

        try {
            List<CartItem> cartItems = getCartFromRedis(customerId);

            if (!cartItems.isEmpty()) {
                log.debug("Cart found in Redis for customer: {}, itemCount={}",
                    customerId, cartItems.size());
                return cartItems;
            }

            log.debug("Cache miss, retrieving from database for customer: {}", customerId);
            cartItems = getCartFromDatabase(customerId);

            if (!cartItems.isEmpty()) {
                saveCartToRedis(customerId, cartItems);
            }

            return cartItems;

        } catch (Exception e) {
            log.error("Failed to get cart for customer: {}", customerId, e);
            return getCartFromDatabase(customerId);
        }
    }

    public void updateItemQuantity(UUID customerId, UpdateCartItemRequest request) {
        validateCustomerId(customerId);
        validateUpdateItemRequest(request);

        try {
            List<CartItem> cartItems = getCartFromRedis(customerId);

            if (cartItems.isEmpty()) {
                cartItems = getCartFromDatabase(customerId);
                if (!cartItems.isEmpty()) {
                    saveCartToRedis(customerId, cartItems);
                }
            }

            CartItem targetItem = cartItems.stream()
                .filter(item -> item.getMenuId().equals(request.getMenuId()))
                .findFirst()
                .orElseThrow(() -> new CartException(ErrorCode.CART_ITEM_NOT_FOUND));

            if (request.getQuantity() <= 0) {
                cartItems.remove(targetItem);
                log.debug("Removed item from cart: customerId={}, menuId={}",
                    customerId, request.getMenuId());
            } else {
                targetItem.setQuantity(request.getQuantity());
                log.debug("Updated item quantity: customerId={}, menuId={}, quantity={}",
                    customerId, request.getMenuId(), request.getQuantity());
            }

            saveCartToRedis(customerId, cartItems);
            syncToDatabaseAsync(customerId, cartItems);

            log.info("Successfully updated cart item: customerId={}, menuId={}",
                customerId, request.getMenuId());

        } catch (CartException e) {
            log.warn("Cart update failed: customerId={}, error={}", customerId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to update cart item quantity for customer: {}", customerId, e);
            throw new CartException(ErrorCode.CART_NOT_FOUND);
        }
    }

    public void removeItem(UUID customerId, UUID menuId) {
        validateCustomerId(customerId);
        if (menuId == null) {
            throw new CartException(ErrorCode.INVALID_CART_ITEM_REQUEST);
        }

        try {
            List<CartItem> cartItems = getCart(customerId);

            boolean removed = cartItems.removeIf(item -> item.getMenuId().equals(menuId));

            if (!removed) {
                throw new CartException(ErrorCode.CART_NOT_FOUND);
            }

            saveCartToRedis(customerId, cartItems);
            syncToDatabaseAsync(customerId, cartItems);

            log.info("Successfully removed item from cart: customerId={}, menuId={}",
                customerId, menuId);

        } catch (CartException e) {
            log.warn("Cart item removal failed: customerId={}, error={}", customerId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to remove item from cart for customer: {}", customerId, e);
            throw new CartException(ErrorCode.CART_NOT_FOUND);
        }
    }

    @Transactional
    public void clearCart(UUID customerId) {
        validateCustomerId(customerId);

        try {
            invalidateCartCache(customerId);
            syncToDatabaseAsync(customerId, new ArrayList<>());

            log.info("Successfully cleared cart for customer: {}", customerId);

        } catch (Exception e) {
            log.error("Failed to clear cart for customer: {}", customerId, e);
            throw new CartException(ErrorCode.CART_NOT_FOUND);
        }
    }

    @Transactional
    public void invalidateCartAfterOrder(UUID customerId) {
        validateCustomerId(customerId);

        try {
            invalidateCartCache(customerId);
            syncToDatabaseAsync(customerId, new ArrayList<>());
            log.info("Cart invalidated after order completion for customer: {}", customerId);
        } catch (Exception e) {
            log.error("Failed to invalidate cart after order for customer: {}", customerId, e);
        }
    }

    private void validateCustomerId(UUID customerId) {
        if (customerId == null) {
            throw new CartException(ErrorCode.INVALID_CUSTOMER_ID);
        }
    }

    private String getCartKey(UUID customerId) {
        return CART_KEY_PREFIX + customerId.toString();
    }

    private boolean isRedisAvailable() {
        try {
            assert redisTemplate.getConnectionFactory() != null;
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (RedisConnectionFailureException e) {
            log.debug("Redis connection failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.debug("Redis availability check failed: {}", e.getMessage());
            return false;
        }
    }

    private List<CartItem> getCartFromRedis(UUID customerId) {
        try {
            if (!isRedisAvailable()) {
                return new ArrayList<>();
            }

            String cartKey = getCartKey(customerId);
            Object cartData = redisTemplate.opsForValue().get(cartKey);

            if (cartData instanceof List<?> rawList) {
                List<CartItem> cartItems = new ArrayList<>();

                for (Object item : rawList) {
                    if (item instanceof CartItem) {
                        cartItems.add((CartItem) item);
                    } else if (item instanceof Map) {
                        cartItems.add(convertMapToCartItem((Map<?, ?>) item));
                    }
                }

                return cartItems;
            }

            return new ArrayList<>();

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable, falling back to database for customer: {}", customerId);
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to get cart from Redis for customer: {}, error: {}",
                customerId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveCartToRedis(UUID customerId, List<CartItem> cartItems) {
        try {
            if (!isRedisAvailable()) {
                log.debug("Redis unavailable, skipping cache update for customer: {}", customerId);
                return;
            }

            String cartKey = getCartKey(customerId);

            if (cartItems.isEmpty()) {
                redisTemplate.delete(cartKey);
                log.debug("Deleted empty cart from cache for customer: {}", customerId);
            } else {
                redisTemplate.opsForValue().set(cartKey, cartItems, CART_TTL);
                log.debug("Saved cart to cache for customer: {}, itemCount={}",
                    customerId, cartItems.size());
            }

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable during cart save for customer: {}", customerId);
        } catch (Exception e) {
            log.error("Failed to save cart to Redis for customer: {}", customerId, e);
        }
    }

    private void invalidateCartCache(UUID customerId) {
        try {
            if (isRedisAvailable()) {
                String cartKey = getCartKey(customerId);
                redisTemplate.delete(cartKey);
                log.debug("Invalidated cart cache for customer: {}", customerId);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate cart cache for customer: {}", customerId, e);
        }
    }

    private List<CartItem> getCartFromDatabase(UUID customerId) {
        try {
            Optional<Cart> cartOptional = cartRepository.findByCustomerId(customerId);

            if (cartOptional.isPresent()) {
                Cart cart = cartOptional.get();
                List<CartItem> items = convertCartEntityToItems(cart);
                log.debug("Retrieved cart from database for customer: {}, itemCount={}",
                    customerId, items.size());
                return items;
            }

            return new ArrayList<>();

        } catch (Exception e) {
            log.error("Failed to get cart from database for customer: {}", customerId, e);
            return new ArrayList<>();
        }
    }

    private void syncToDatabaseAsync(UUID customerId, List<CartItem> cartItems) {
        try {
            pendingChanges.put(customerId, cartItems);
            log.debug("Added to batch queue for customer: {}, queue size: {}",
                customerId, pendingChanges.size());

        } catch (Exception e) {
            log.warn("Failed to add to batch queue for customer: {}, falling back to immediate sync",
                customerId, e);
            syncToDatabase(customerId, cartItems);
        }
    }

    @Transactional
    protected void syncToDatabase(UUID customerId, List<CartItem> cartItems) {
        try {
            if (cartItems.isEmpty()) {
                cartRepository.deleteByCustomerId(customerId);
                log.debug("Deleted empty cart from database for customer: {}", customerId);
            } else {
                Cart cart = convertCartItemsToEntity(customerId, cartItems);
                cartRepository.save(cart);
                log.debug("Synced cart to database for customer: {}, itemCount={}",
                    customerId, cartItems.size());
            }

        } catch (Exception e) {
            log.error("Failed to sync cart to database for customer: {}", customerId, e);
            throw new CartException(ErrorCode.CART_NOT_FOUND);
        }
    }

    /**
     * 트랜잭션 없이 데이터베이스 동기화 (스케줄러에서 사용)
     */
    protected void syncToDatabaseWithTransaction(UUID customerId, List<CartItem> cartItems) {
        try {
            if (cartItems.isEmpty()) {
                cartRepository.deleteByCustomerId(customerId);
                log.debug("Deleted empty cart from database for customer: {}", customerId);
            } else {
                Cart cart = convertCartItemsToEntity(customerId, cartItems);
                cartRepository.save(cart);
                log.debug("Synced cart to database for customer: {}, itemCount={}",
                    customerId, cartItems.size());
            }

        } catch (Exception e) {
            log.error("Failed to sync cart to database for customer: {}", customerId, e);
            throw new CartException(ErrorCode.CART_NOT_FOUND);
        }
    }

    private CartItem convertMapToCartItem(Map<?, ?> map) {
        try {
            return CartItem.builder()
                .menuId(UUID.fromString(map.get("menuId").toString()))
                .menuName(map.get("menuName").toString())
                .quantity(Integer.valueOf(map.get("quantity").toString()))
                .price(Integer.valueOf(map.get("price").toString()))
                .storeId(UUID.fromString(map.get("storeId").toString()))
                .build();
        } catch (Exception e) {
            log.error("Failed to convert map to CartItem: {}", map, e);
            throw new CartException(ErrorCode.CART_NOT_FOUND);
        }
    }

    private List<CartItem> convertCartEntityToItems(Cart cart) {
        if (cart == null || cart.getCartItems() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(cart.getCartItems());
    }

    private Cart convertCartItemsToEntity(UUID customerId, List<CartItem> cartItems) {
        Optional<Cart> existingCart = cartRepository.findByCustomerId(customerId);
        Cart cart;

        if (existingCart.isPresent()) {
            cart = existingCart.get();
            cart.setCartItems(new ArrayList<>(cartItems));
        } else {
            cart = Cart.builder()
                .cartItems(new ArrayList<>(cartItems))
                .customerId(customerId)
                .build();
        }

        return cart;
    }

    private void validateAddItemRequest(AddCartItemRequest request) {
        if (request == null) {
            throw new CartException(ErrorCode.INVALID_UPDATE_REQUEST);
        }
        if (request.getQuantity() <= 0) {
            throw new CartException(ErrorCode.INVALID_UPDATE_REQUEST);
        }
        if (request.getPrice() < 0) {
            throw new CartException(ErrorCode.INVALID_UPDATE_REQUEST);
        }
        if (request.getMenuName() == null || request.getMenuName().trim().isEmpty()) {
            throw new CartException(ErrorCode.INVALID_UPDATE_REQUEST);
        }
    }

    private void validateUpdateItemRequest(UpdateCartItemRequest request) {
        if (request == null) {
            throw new CartException(ErrorCode.INVALID_UPDATE_REQUEST);
        }
        if (request.getQuantity() < 0) {
            throw new CartException(ErrorCode.INVALID_UPDATE_REQUEST);
        }
    }

    private void validateStoreConsistency(List<CartItem> cartItems, UUID newStoreId) {
        if (!cartItems.isEmpty()) {
            UUID existingStoreId = cartItems.getFirst().getStoreId();
            if (!existingStoreId.equals(newStoreId)) {
                throw new CartException(ErrorCode.CART_STORE_MISMATCH);
            }
        }
    }

    @PostConstruct
    public void initBatchProcessor() {
        scheduler.scheduleAtFixedRate(
            this::processBatchChanges,
            BATCH_INTERVAL_SECONDS,
            BATCH_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );

        log.info("Cart batch processor started with {} seconds interval", BATCH_INTERVAL_SECONDS);
    }

    @PreDestroy
    public void cleanup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processBatchChanges() {
        if (pendingChanges.isEmpty()) return;

        Map<UUID, List<CartItem>> batch = null;

        try {
            batch = new HashMap<>(pendingChanges);
            pendingChanges.clear();

            log.info("Processing batch DB sync for {} customers", batch.size());

            batch.forEach((customerId, cartItems) -> {
                TransactionStatus transaction = null;
                try {
                    // 수동으로 트랜잭션 시작
                    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
                    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    transaction = transactionManager.getTransaction(def);
                    
                    syncToDatabaseWithTransaction(customerId, cartItems);
                    
                    // 트랜잭션 커밋
                    transactionManager.commit(transaction);
                    log.debug("Batch DB sync completed for customer: {}", customerId);

                } catch (Exception e) {
                    // 트랜잭션 롤백
                    if (transaction != null) {
                        transactionManager.rollback(transaction);
                    }
                    log.error("Batch DB sync failed for customer: {}", customerId, e);
                    pendingChanges.put(customerId, cartItems);
                    log.warn("Re-added failed sync to batch queue for customer: {}", customerId);
                }
            });

            log.info("Batch DB sync completed for {} customers", batch.size());

        } catch (Exception e) {
            log.error("Failed to process batch changes", e);
            if (batch != null) {
                pendingChanges.putAll(batch);
                log.warn("Re-added all failed batch items to queue due to processing error");
            }
        }
    }

    public Map<String, Object> getBatchQueueStatus() {
        Map<String, Object> status = new HashMap<>();

        int pendingCount = pendingChanges.size();
        List<UUID> pendingIds = new ArrayList<>(pendingChanges.keySet());

        status.put("pendingChangesCount", pendingCount);
        status.put("batchIntervalSeconds", BATCH_INTERVAL_SECONDS);
        status.put("pendingCustomerIds", pendingIds);
        status.put("lastProcessedTime", System.currentTimeMillis());

        return status;
    }

    public void forceBatchProcessing() {
        log.info("Force batch processing triggered");
        synchronized (this) {
            if (!pendingChanges.isEmpty()) {
                processBatchChanges();
            } else {
                log.info("No pending changes to process");
            }
        }
    }
}
