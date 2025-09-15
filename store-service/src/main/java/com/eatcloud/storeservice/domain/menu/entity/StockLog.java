package com.eatcloud.storeservice.domain.menu.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_stock_logs")
@Getter
public class StockLog {

    @Id
    @Column(name = "log_id", nullable = false)
    private UUID id;

    @Column(name = "menu_id", nullable = false)
    private UUID menuId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "order_line_id")
    private UUID orderLineId;

    @Column(name = "action", nullable = false, length = 16)
    private String action; // RESERVE, CONFIRM, CANCEL

    @Column(name = "change_amount", nullable = false)
    private int changeAmount; // -N, 0, +N

    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static StockLog of(String action, UUID menuId, UUID orderId,
                              UUID lineId, int delta, String reason) {
        StockLog l = new StockLog();
        l.id = UUID.randomUUID();
        l.action = action;
        l.menuId = menuId;
        l.orderId = orderId;
        l.orderLineId = lineId;
        l.changeAmount = delta;
        l.reason = reason;
        return l;
    }
}
