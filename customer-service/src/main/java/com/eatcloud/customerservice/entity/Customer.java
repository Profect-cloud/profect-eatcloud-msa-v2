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

	@OneToMany(mappedBy = "customer", fetch = FetchType.LAZY)
	@Builder.Default
	private List<Address> addresses = new ArrayList<>();

	public void reservePoints(Integer pointsToReserve) {
		if (pointsToReserve == null || pointsToReserve <= 0) {
			throw new IllegalArgumentException("예약할 포인트는 0보다 커야 합니다.");
		}
		
		if (this.points < pointsToReserve) {
			throw new IllegalStateException("보유 포인트가 부족합니다. 보유: " + this.points + ", 필요: " + pointsToReserve);
		}
		
		this.points -= pointsToReserve;
	}
	
	public void deductReservedPoints(Integer pointsToDeduct) {
		if (pointsToDeduct == null || pointsToDeduct <= 0) {
			throw new IllegalArgumentException("차감할 포인트는 0보다 커야 합니다.");
		}
	}
	
	public void addPoints(Integer pointsToAdd) {
		if (pointsToAdd == null || pointsToAdd <= 0) {
			throw new IllegalArgumentException("추가할 포인트는 0보다 커야 합니다.");
		}
		
		this.points += pointsToAdd;
	}
	
	public void refundReservedPoints(Integer pointsToRefund) {
		if (pointsToRefund == null || pointsToRefund <= 0) {
			throw new IllegalArgumentException("환불할 포인트는 0보다 커야 합니다.");
		}
		
		this.points += pointsToRefund;
	}
	
	public Integer getTotalPoints() {
		return this.points;
	}
}
