package com.eatcloud.storeservice.domain.store.entity;

import com.eatcloud.autotime.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;


import java.util.UUID;

@Entity
@SQLRestriction("deleted_at is null")
@Table(name = "delivery_areas")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryArea extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "area_id")
	private UUID areaId;

	@Column(name = "area_name", nullable = false, length = 100)
	private String areaName;
}