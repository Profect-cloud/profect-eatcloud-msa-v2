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

}
