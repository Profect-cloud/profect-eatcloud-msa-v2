package com.eatcloud.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;
import com.eatcloud.orderservice.dto.OrderMenu;
import com.eatcloud.autotime.BaseTimeEntity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;
import java.util.UUID;
import java.util.List;

@Entity
@Table(name = "p_orders")
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "order_id")
	private UUID orderId;

	@Column(name = "order_number", nullable = false, length = 50, unique = true)
	private String orderNumber;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "order_menu_list", nullable = false, columnDefinition = "jsonb")
	private List<OrderMenu> orderMenuList;

	@Column(name = "customer_id", nullable = false)
	private UUID customerId;

	@Column(name = "store_id", nullable = false)
	private UUID storeId;

	@Column(name = "payment_id")
	private UUID paymentId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_status", referencedColumnName = "code")
	private OrderStatusCode orderStatusCode;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_type", referencedColumnName = "code")
	private OrderTypeCode orderTypeCode;

	@Column(name = "total_price", nullable = false)
	private Integer totalPrice;

	@Column(name = "use_points", nullable = false)
	@Builder.Default
	private Boolean usePoints = false;

	@Column(name = "points_to_use", nullable = false)
	@Builder.Default
	private Integer pointsToUse = 0;

	@Column(name = "final_payment_amount", nullable = false)
	private Integer finalPaymentAmount;
}
