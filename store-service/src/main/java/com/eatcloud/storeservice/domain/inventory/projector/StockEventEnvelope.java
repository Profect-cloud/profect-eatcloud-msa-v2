// package: com.eatcloud.storeservice.domain.inventory.projector
package com.eatcloud.storeservice.domain.inventory.projector;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter; import lombok.Setter;

import java.util.UUID;

@Getter @Setter
public class StockEventEnvelope {
    private UUID id;               // outbox id
    private String eventType;      // stock.reserved / stock.committed / stock.released / stock.insufficient / stock.returned / stock.adjusted
    private String aggregateType;  // INVENTORY_ITEM
    private UUID aggregateId;      // menuId
    private JsonNode payload;      // orderId, orderLineId, qty/delta/reason...

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private String createdAt;
}
