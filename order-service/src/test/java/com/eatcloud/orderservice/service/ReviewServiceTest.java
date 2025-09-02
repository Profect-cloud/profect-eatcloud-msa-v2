package com.eatcloud.orderservice.service;

import com.eatcloud.orderservice.dto.request.ReviewRequestDto;
import com.eatcloud.orderservice.dto.response.ReviewResponseDto;
import com.eatcloud.orderservice.entity.Order;
import com.eatcloud.orderservice.entity.Review;
import com.eatcloud.orderservice.repository.OrderRepository;
import com.eatcloud.orderservice.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 단위 테스트")
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ReviewService reviewService;

    private UUID customerId;
    private UUID orderId;
    private UUID storeId;
    private UUID reviewId;
    private Order order;
    private Review review;
    private ReviewRequestDto reviewRequest;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        reviewId = UUID.randomUUID();

        order = Order.builder()
                .orderId(orderId)
                .customerId(customerId)
                .storeId(storeId)
                .orderNumber("ORD-20241215-ABCDE")
                .build();

        reviewRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("5.0"),
                "정말 맛있었습니다!"
        );

        review = Review.builder()
                .reviewId(reviewId)
                .order(order)  // Order 엔티티 참조
                .rating(new BigDecimal("5.0"))
                .content("정말 맛있었습니다!")
                .build();
    }

    @Test
    @DisplayName("리뷰 작성 - 성공")
    void createReview_Success() {
        given(orderRepository.findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId))
                .willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderOrderIdAndTimeData_DeletedAtIsNull(orderId)).willReturn(false);
        given(reviewRepository.save(any(Review.class))).willReturn(review);


        ReviewResponseDto response = reviewService.createReview(customerId, reviewRequest);

        assertThat(response).isNotNull();
        assertThat(response.reviewId()).isEqualTo(reviewId);
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.rating()).isEqualTo(new BigDecimal("5.0"));
        assertThat(response.content()).isEqualTo("정말 맛있었습니다!");

        verify(orderRepository).findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId);
        verify(reviewRepository).existsByOrderOrderIdAndTimeData_DeletedAtIsNull(orderId);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 작성 - 주문 없음 예외")
    void createReview_OrderNotFound_ThrowsException() {
        given(orderRepository.findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId))
                .willReturn(Optional.empty());


        assertThatThrownBy(() -> reviewService.createReview(customerId, reviewRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("해당 주문이 없거나 권한이 없습니다.");

        verify(orderRepository).findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId);
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 작성 - 이미 작성된 리뷰 예외")
    void createReview_AlreadyExists_ThrowsException() {

        given(orderRepository.findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId))
                .willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderOrderIdAndTimeData_DeletedAtIsNull(orderId)).willReturn(true);


        assertThatThrownBy(() -> reviewService.createReview(customerId, reviewRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("이미 해당 주문에 대한 리뷰가 존재합니다.");

        verify(reviewRepository).existsByOrderOrderIdAndTimeData_DeletedAtIsNull(orderId);
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 작성 - 잘못된 평점 (0.5점)")
    void createReview_InvalidRatingLow_ThrowsException() {

        ReviewRequestDto invalidRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("0.5"),
                "평점이 잘못됨"
        );

        assertThatThrownBy(() -> reviewService.createReview(customerId, invalidRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("평점은 1.0~5.0 사이의 값이어야 합니다.");

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 작성 - 잘못된 평점 (5.5점)")
    void createReview_InvalidRatingHigh_ThrowsException() {

        ReviewRequestDto invalidRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("5.5"),
                "평점이 잘못됨"
        );


        assertThatThrownBy(() -> reviewService.createReview(customerId, invalidRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("평점은 1.0~5.0 사이의 값이어야 합니다.");

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 작성 - 빈 내용")
    void createReview_EmptyContent_ThrowsException() {
        // Given
        ReviewRequestDto invalidRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("5.0"),
                ""
        );

        assertThatThrownBy(() -> reviewService.createReview(customerId, invalidRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("리뷰 내용은 필수입니다.");

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 작성 - null 내용")
    void createReview_NullContent_ThrowsException() {
        ReviewRequestDto invalidRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("5.0"),
                null
        );

        assertThatThrownBy(() -> reviewService.createReview(customerId, invalidRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("리뷰 내용은 필수입니다.");

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("매장별 리뷰 조회 - 성공")
    void getReviewsByStore_Success() {
        Order order1 = Order.builder().orderId(UUID.randomUUID()).storeId(storeId).build();
        Order order2 = Order.builder().orderId(UUID.randomUUID()).storeId(storeId).build();

        List<Review> storeReviews = Arrays.asList(
                Review.builder()
                        .reviewId(UUID.randomUUID())
                        .order(order1)
                        .rating(new BigDecimal("5.0"))
                        .content("맛있어요!")
                        .build(),
                Review.builder()
                        .reviewId(UUID.randomUUID())
                        .order(order2)
                        .rating(new BigDecimal("4.0"))
                        .content("좋아요!")
                        .build()
        );

        given(reviewRepository.findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId))
                .willReturn(storeReviews);

        List<ReviewResponseDto> responses = reviewService.getReviewsByStore(storeId);

        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).rating()).isEqualTo(new BigDecimal("5.0"));
        assertThat(responses.get(0).content()).isEqualTo("맛있어요!");
        assertThat(responses.get(1).rating()).isEqualTo(new BigDecimal("4.0"));
        assertThat(responses.get(1).content()).isEqualTo("좋아요!");

        verify(reviewRepository).findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId);
    }

    @Test
    @DisplayName("고객별 리뷰 조회 - 성공")
    void getReviewsByCustomer_Success() {

        Order customerOrder1 = Order.builder().orderId(UUID.randomUUID()).customerId(customerId).build();
        Order customerOrder2 = Order.builder().orderId(UUID.randomUUID()).customerId(customerId).build();

        List<Review> customerReviews = Arrays.asList(
                Review.builder()
                        .reviewId(UUID.randomUUID())
                        .order(customerOrder1)
                        .rating(new BigDecimal("5.0"))
                        .content("맛있었어요!")
                        .build(),
                Review.builder()
                        .reviewId(UUID.randomUUID())
                        .order(customerOrder2)
                        .rating(new BigDecimal("4.0"))
                        .content("괜찮았어요!")
                        .build()
        );

        given(reviewRepository.findByOrderCustomerIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(customerId))
                .willReturn(customerReviews);

        List<ReviewResponseDto> responses = reviewService.getReviewsByCustomer(customerId);

        assertThat(responses).isNotNull();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).rating()).isEqualTo(new BigDecimal("5.0"));
        assertThat(responses.get(0).content()).isEqualTo("맛있었어요!");

        verify(reviewRepository).findByOrderCustomerIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(customerId);
    }

    @Test
    @DisplayName("리뷰 수정 - 성공")
    void updateReview_Success() {
        ReviewRequestDto updateRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("4.0"),
                "수정된 리뷰 내용입니다."
        );

        Review updatedReview = Review.builder()
                .reviewId(reviewId)
                .order(order)
                .rating(new BigDecimal("4.0"))
                .content("수정된 리뷰 내용입니다.")
                .build();

        given(reviewRepository.findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId))
                .willReturn(Optional.of(review));
        given(reviewRepository.save(any(Review.class))).willReturn(updatedReview);

        ReviewResponseDto response = reviewService.updateReview(customerId, reviewId, updateRequest);

        assertThat(response).isNotNull();
        assertThat(response.reviewId()).isEqualTo(reviewId);
        assertThat(response.rating()).isEqualTo(new BigDecimal("4.0"));
        assertThat(response.content()).isEqualTo("수정된 리뷰 내용입니다.");

        verify(reviewRepository).findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 수정 - 리뷰 없음 또는 권한 없음")
    void updateReview_NotFoundOrNoPermission_ThrowsException() {
        ReviewRequestDto updateRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("4.0"),
                "수정된 리뷰 내용입니다."
        );

        given(reviewRepository.findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.updateReview(customerId, reviewId, updateRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("해당 리뷰가 없거나 수정 권한이 없습니다.");

        verify(reviewRepository).findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId);
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("리뷰 삭제 - 성공")
    void deleteReview_Success() {

        given(reviewRepository.findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId))
                .willReturn(Optional.of(review));

        reviewService.deleteReview(customerId, reviewId);

        verify(reviewRepository).findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId);
        verify(reviewRepository).delete(review);
    }

    @Test
    @DisplayName("리뷰 삭제 - 리뷰 없음 또는 권한 없음")
    void deleteReview_NotFoundOrNoPermission_ThrowsException() {

        given(reviewRepository.findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.deleteReview(customerId, reviewId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("해당 리뷰가 없거나 삭제 권한이 없습니다.");

        verify(reviewRepository).findByReviewIdAndOrderCustomerIdAndTimeData_DeletedAtIsNull(reviewId, customerId);
        verify(reviewRepository, never()).delete(any(Review.class));
    }

    @Test
    @DisplayName("매장 평점 평균 계산 - 성공")
    void calculateAverageRating_Success() {
        List<Review> storeReviews = Arrays.asList(
                Review.builder().rating(new BigDecimal("5.0")).build(),
                Review.builder().rating(new BigDecimal("4.0")).build(),
                Review.builder().rating(new BigDecimal("3.0")).build(),
                Review.builder().rating(new BigDecimal("5.0")).build()
        );

        given(reviewRepository.findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId))
                .willReturn(storeReviews);

        BigDecimal averageRating = reviewService.calculateAverageRating(storeId);

        assertThat(averageRating).isEqualByComparingTo(new BigDecimal("4.25"));

        verify(reviewRepository).findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId);
    }

    @Test
    @DisplayName("매장 평점 평균 계산 - 리뷰 없음")
    void calculateAverageRating_NoReviews_ReturnsZero() {

        given(reviewRepository.findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId))
                .willReturn(Arrays.asList());


        BigDecimal averageRating = reviewService.calculateAverageRating(storeId);


        assertThat(averageRating).isEqualByComparingTo(BigDecimal.ZERO);

        verify(reviewRepository).findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId);
    }

    @Test
    @DisplayName("매장 리뷰 통계 - 평점별 개수")
    void getReviewStatistics_Success() {

        List<Review> allReviews = Arrays.asList(
                Review.builder().rating(new BigDecimal("5.0")).build(),
                Review.builder().rating(new BigDecimal("5.0")).build(),
                Review.builder().rating(new BigDecimal("4.0")).build(),
                Review.builder().rating(new BigDecimal("3.0")).build(),
                Review.builder().rating(new BigDecimal("2.0")).build()
        );

        given(reviewRepository.findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId))
                .willReturn(allReviews);


        Map<String, Object> statistics = reviewService.getReviewStatistics(storeId);


        assertThat(statistics).isNotNull();
        assertThat(statistics.get("totalReviews")).isEqualTo(5);
        assertThat((BigDecimal) statistics.get("averageRating")).isEqualByComparingTo(new BigDecimal("3.80")); // (5+5+4+3+2)/5
        assertThat(statistics.get("fiveStarCount")).isEqualTo(2);
        assertThat(statistics.get("fourStarCount")).isEqualTo(1);
        assertThat(statistics.get("threeStarCount")).isEqualTo(1);
        assertThat(statistics.get("twoStarCount")).isEqualTo(1);
        assertThat(statistics.get("oneStarCount")).isEqualTo(0);

        verify(reviewRepository).findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId);
    }

    @Test
    @DisplayName("리뷰 내용 길이 검증 - 너무 긴 내용")
    void createReview_ContentTooLong_ThrowsException() {

        String longContent = "a".repeat(1001);
        ReviewRequestDto longContentRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("5.0"),
                longContent
        );


        assertThatThrownBy(() -> reviewService.createReview(customerId, longContentRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("리뷰 내용은 1000자 이하로 작성해주세요.");

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("부적절한 내용 필터링 테스트")
    void createReview_ContainsInappropriateContent_ThrowsException() {

        ReviewRequestDto inappropriateRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("1.0"),
                "이 음식은 정말 바보같아요"
        );

        given(orderRepository.findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId))
                .willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderOrderIdAndTimeData_DeletedAtIsNull(orderId)).willReturn(false);

        assertThatThrownBy(() -> reviewService.createReview(customerId, inappropriateRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("부적절한 내용이 포함되어 있습니다.");

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("평점 경계값 테스트 - 1.0점")
    void createReview_RatingBoundaryMinimum_Success() {

        ReviewRequestDto minRatingRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("1.0"),  // 최소값
                "최소 평점 테스트"
        );

        Review savedReview = Review.builder()
                .reviewId(reviewId)
                .order(order)
                .rating(new BigDecimal("1.0"))
                .content("최소 평점 테스트")
                .build();

        given(orderRepository.findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId))
                .willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderOrderIdAndTimeData_DeletedAtIsNull(orderId)).willReturn(false);
        given(reviewRepository.save(any(Review.class))).willReturn(savedReview);


        ReviewResponseDto response = reviewService.createReview(customerId, minRatingRequest);


        assertThat(response).isNotNull();
        assertThat(response.rating()).isEqualTo(new BigDecimal("1.0"));

        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("평점 경계값 테스트 - 5.0점")
    void createReview_RatingBoundaryMaximum_Success() {

        ReviewRequestDto maxRatingRequest = new ReviewRequestDto(
                orderId,
                new BigDecimal("5.0"),
                "최대 평점 테스트"
        );

        Review savedReview = Review.builder()
                .reviewId(reviewId)
                .order(order)
                .rating(new BigDecimal("5.0"))
                .content("최대 평점 테스트")
                .build();

        given(orderRepository.findByOrderIdAndCustomerIdAndTimeData_DeletedAtIsNull(orderId, customerId))
                .willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderOrderIdAndTimeData_DeletedAtIsNull(orderId)).willReturn(false);
        given(reviewRepository.save(any(Review.class))).willReturn(savedReview);

        ReviewResponseDto response = reviewService.createReview(customerId, maxRatingRequest);

        assertThat(response).isNotNull();
        assertThat(response.rating()).isEqualTo(new BigDecimal("5.0"));

        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("매장별 리뷰 조회 - 빈 결과")
    void getReviewsByStore_EmptyResult() {
        given(reviewRepository.findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId))
                .willReturn(Arrays.asList());

        List<ReviewResponseDto> responses = reviewService.getReviewsByStore(storeId);

        assertThat(responses).isNotNull();
        assertThat(responses).isEmpty();

        verify(reviewRepository).findByOrderStoreIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(storeId);
    }

    @Test
    @DisplayName("고객별 리뷰 조회 - 빈 결과")
    void getReviewsByCustomer_EmptyResult() {
        given(reviewRepository.findByOrderCustomerIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(customerId))
                .willReturn(Arrays.asList());

        List<ReviewResponseDto> responses = reviewService.getReviewsByCustomer(customerId);

        assertThat(responses).isNotNull();
        assertThat(responses).isEmpty();

        verify(reviewRepository).findByOrderCustomerIdAndTimeData_DeletedAtIsNullOrderByTimeData_CreatedAtDesc(customerId);
    }
}
