package com.eatcloud.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;
import com.eatcloud.orderservice.dto.CartItem;
import com.eatcloud.autotime.BaseTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "p_cart")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "cart_id")
	private UUID cartId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "cart_items", nullable = false, columnDefinition = "jsonb")
	private List<CartItem> cartItems;

	@Column(name = "customer_id", nullable = false, unique = true)
	private UUID customerId;
}
