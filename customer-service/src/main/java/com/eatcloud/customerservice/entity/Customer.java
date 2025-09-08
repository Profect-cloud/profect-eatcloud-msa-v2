package com.eatcloud.customerservice.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.eatcloud.autotime.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "p_customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@Column(name = "name", nullable = false, length = 20, unique = true)
	private String name;

	@Column(name = "nickname", length = 100)
	private String nickname;

	@Column(name = "email", length = 255)
	private String email;

	@Column(name = "password", nullable = false, length = 255)
	private String password;

	@Column(name = "phone_number", length = 18)
	private String phoneNumber;

	@Column(name = "points")
	@Builder.Default
	private Integer points = 0;

	@Column(name = "reserved_points")
	@Builder.Default
	private Integer reservedPoints = 0;

	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	@Builder.Default
	private List<Address> addresses = new ArrayList<>();

	/**
	 * 포인트 예약 (실제 차감하지 않고 예약만)
	 */
	public void reservePoints(Integer pointsToReserve) {
		if (pointsToReserve == null || pointsToReserve <= 0) {
			throw new IllegalArgumentException("예약할 포인트는 0보다 커야 합니다.");
		}
		
		// 사용 가능한 포인트 = 보유 포인트 - 이미 예약된 포인트
		Integer availablePoints = this.points - this.reservedPoints;
		if (availablePoints < pointsToReserve) {
			throw new IllegalStateException(String.format(
				"사용 가능한 포인트가 부족합니다. 보유: %d, 예약됨: %d, 사용가능: %d, 필요: %d", 
				this.points, this.reservedPoints, availablePoints, pointsToReserve));
		}
		
		// 예약 포인트만 증가, 실제 포인트는 차감하지 않음
		this.reservedPoints += pointsToReserve;
	}

	/**
	 * 예약된 포인트를 실제로 차감 (결제 완료 시)
	 */
	public void processReservedPoints(Integer pointsToProcess) {
		if (pointsToProcess == null || pointsToProcess <= 0) {
			throw new IllegalArgumentException("처리할 포인트는 0보다 커야 합니다.");
		}
		
		if (this.reservedPoints < pointsToProcess) {
			throw new IllegalStateException(String.format(
				"예약된 포인트가 부족합니다. 예약됨: %d, 처리요청: %d", 
				this.reservedPoints, pointsToProcess));
		}
		
		// 예약된 포인트에서 차감하고, 실제 포인트에서도 차감
		this.reservedPoints -= pointsToProcess;
		this.points -= pointsToProcess;
	}

	/**
	 * 포인트 예약 취소 (주문 취소 시)
	 */
	public void cancelReservedPoints(Integer pointsToCancel) {
		if (pointsToCancel == null || pointsToCancel <= 0) {
			throw new IllegalArgumentException("취소할 포인트는 0보다 커야 합니다.");
		}
		
		if (this.reservedPoints < pointsToCancel) {
			throw new IllegalStateException(String.format(
				"취소할 예약 포인트가 부족합니다. 예약됨: %d, 취소요청: %d", 
				this.reservedPoints, pointsToCancel));
		}
		
		// 예약된 포인트만 감소
		this.reservedPoints -= pointsToCancel;
	}

	/**
	 * 사용 가능한 포인트 조회
	 */
	public Integer getAvailablePoints() {
		return this.points - this.reservedPoints;
	}
	
	/**
	 * 포인트 추가 (적립 등)
	 */
	public void addPoints(Integer pointsToAdd) {
		if (pointsToAdd == null || pointsToAdd <= 0) {
			throw new IllegalArgumentException("추가할 포인트는 0보다 커야 합니다.");
		}
		
		this.points += pointsToAdd;
	}

	/**
	 * 총 보유 포인트 조회
	 */
	public Integer getTotalPoints() {
		return this.points;
	}
}
