package com.eatcloud.managerservice.entity;

import jakarta.persistence.*;
import lombok.*;
import com.eatcloud.autotime.BaseTimeEntity;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "p_managers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLRestriction("deleted_at is null")
public class Manager extends BaseTimeEntity{

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(length = 100)
	private String name;

	@Column(nullable = false, length = 255)
	private String password;

	@Column(name = "phone_number", length = 18)
	private String phoneNumber;

}

