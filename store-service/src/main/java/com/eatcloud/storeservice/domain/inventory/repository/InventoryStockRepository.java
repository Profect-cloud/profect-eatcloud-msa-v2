// InventoryStockRepository.java
package com.eatcloud.storeservice.domain.inventory.repository;

import com.eatcloud.storeservice.domain.inventory.entity.InventoryStock;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, UUID> {

    @Modifying
    @Query("""
        UPDATE InventoryStock s
           SET s.availableQty = s.availableQty - :qty,
               s.reservedQty  = s.reservedQty + :qty,
               s.updatedAt    = CURRENT_TIMESTAMP
         WHERE s.menuId = :menuId
           AND s.isUnlimited = false
           AND s.availableQty >= :qty
    """)
    int reserve(@Param("menuId") UUID menuId, @Param("qty") int qty);

    @Modifying
    @Query("""
        UPDATE InventoryStock s
           SET s.availableQty = s.availableQty + :qty,
               s.reservedQty  = s.reservedQty - :qty,
               s.updatedAt    = CURRENT_TIMESTAMP
         WHERE s.menuId = :menuId
           AND s.isUnlimited = false
    """)
    int release(@Param("menuId") UUID menuId, @Param("qty") int qty);

    @Modifying
    @Query("""
   UPDATE InventoryStock s
      SET s.availableQty = s.availableQty + :delta,
          s.updatedAt = CURRENT_TIMESTAMP
    WHERE s.menuId = :menuId AND s.isUnlimited = false
      AND (:delta >= 0 OR s.availableQty >= ABS(:delta))
""")
    int adjust(@Param("menuId") UUID menuId, @Param("delta") int delta);

    @Modifying
    @Query(value = """
        UPDATE inventory_stock
        SET reserved_qty = reserved_qty - :qty
        WHERE menu_id = :menuId
          AND reserved_qty >= :qty
        """, nativeQuery = true)
    int consume(@Param("menuId") UUID menuId, @Param("qty") int qty);

}
