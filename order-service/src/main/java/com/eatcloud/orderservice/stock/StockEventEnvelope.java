package com.eatcloud.orderservice.stock;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter; import lombok.Setter;
import java.util.UUID;

/** store-service Outbox→Kafka 메시지와 1:1 매핑 */
@Getter @Setter
public class StockEventEnvelope {
    private UUID id;               // p_outbox.id
    private String eventType;      // stock.reserved/committed/released/insufficient/adjusted...
    private String aggregateType;  // INVENTORY_ITEM
    private UUID aggregateId;      // menuId
    private JsonNode payload;      // { orderId, orderLineId, menuId, qty, ... }
    private String createdAt;      // ISO 문자열(필요시 파싱)
    // headers 쓰면 필드 추가 가능 (correlationId 등)
}
