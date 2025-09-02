package com.eatcloud.adminservice.domain.admin.entity;


import com.eatcloud.autotime.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "p_admins")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Admin extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private UUID id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(length = 100)
	private String name;

	@Column(nullable = false)
	private String password;

	@Column(name = "phone_number", length = 18)
	private String phoneNumber;

	@Column(length = 50)
	private String position;
}
