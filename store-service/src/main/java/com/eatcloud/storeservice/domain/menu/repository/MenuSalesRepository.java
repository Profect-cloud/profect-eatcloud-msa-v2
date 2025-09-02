package com.eatcloud.storeservice.domain.menu.repository;

import com.eatcloud.storeservice.domain.menu.entity.DailyMenuSales;
import com.eatcloud.storeservice.domain.menu.entity.DailyMenuSalesId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MenuSalesRepository extends JpaRepository<DailyMenuSales, DailyMenuSalesId> {


    @Query("SELECT " +
           "m.menuName, " +
           "SUM(dms.quantitySold), " +
           "SUM(dms.totalAmount), " +
           "CASE WHEN SUM(dms.quantitySold) > 0 THEN SUM(dms.totalAmount) / SUM(dms.quantitySold) ELSE 0 END " +
           "FROM DailyMenuSales dms " +
           "JOIN dms.menu m " +
           "WHERE dms.storeId = :storeId " +
           "AND dms.saleDate BETWEEN :startDate AND :endDate " +
           "GROUP BY dms.menuId, m.menuName " +
           "ORDER BY SUM(dms.totalAmount) DESC")
    List<Object[]> findTopMenuSalesByStoreIdAndDateRange(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT dms FROM DailyMenuSales dms " +
           "WHERE dms.storeId = :storeId " +
           "AND dms.menuId = :menuId " +
           "AND dms.saleDate BETWEEN :startDate AND :endDate " +
           "ORDER BY dms.saleDate ASC")
    List<DailyMenuSales> findMenuSalesTrendByStoreIdAndMenuId(
            @Param("storeId") UUID storeId,
            @Param("menuId") UUID menuId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    @Query("SELECT COUNT(DISTINCT dms.menuId) " +
           "FROM DailyMenuSales dms " +
           "WHERE dms.storeId = :storeId " +
           "AND dms.saleDate BETWEEN :startDate AND :endDate")
    Long countDistinctMenusByStoreIdAndDateRange(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
