package com.eatcloud.storeservice.domain.store.repository;

import com.eatcloud.storeservice.domain.store.entity.DailyStoreSales;
import com.eatcloud.storeservice.domain.store.entity.DailyStoreSalesId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SalesStatisticsRepository extends JpaRepository<DailyStoreSales, DailyStoreSalesId> {


    @Query("SELECT d FROM DailyStoreSales d WHERE d.storeId = :storeId " +
           "AND d.saleDate BETWEEN :startDate AND :endDate " +
           "ORDER BY d.saleDate ASC")
    List<DailyStoreSales> findByStoreIdAndDateRange(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    @Query("SELECT " +
           "COUNT(d), " +
           "SUM(d.orderCount), " +
           "SUM(d.totalAmount), " +
           "AVG(d.totalAmount), " +
           "MAX(d.totalAmount), " +
           "MIN(d.totalAmount) " +
           "FROM DailyStoreSales d " +
           "WHERE d.storeId = :storeId " +
           "AND d.saleDate BETWEEN :startDate AND :endDate")
    Object[] findSalesSummaryByStoreIdAndDateRange(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    @Query("SELECT " +
           "YEAR(d.saleDate), " +
           "MONTH(d.saleDate), " +
           "SUM(d.orderCount), " +
           "SUM(d.totalAmount) " +
           "FROM DailyStoreSales d " +
           "WHERE d.storeId = :storeId " +
           "AND d.saleDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(d.saleDate), MONTH(d.saleDate) " +
           "ORDER BY YEAR(d.saleDate) DESC, MONTH(d.saleDate) DESC")
    List<Object[]> findMonthlySalesByStoreId(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    @Query("SELECT d FROM DailyStoreSales d WHERE d.storeId = :storeId " +
           "AND d.saleDate >= :startDate " +
           "ORDER BY d.saleDate DESC")
    List<DailyStoreSales> findRecentSalesByStoreId(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate
    );

    @Query("SELECT " +
           "DAYOFWEEK(d.saleDate), " +
           "AVG(d.totalAmount), " +
           "AVG(d.orderCount) " +
           "FROM DailyStoreSales d " +
           "WHERE d.storeId = :storeId " +
           "AND d.saleDate BETWEEN :startDate AND :endDate " +
           "GROUP BY DAYOFWEEK(d.saleDate) " +
           "ORDER BY DAYOFWEEK(d.saleDate)")
    List<Object[]> findWeeklyAverageSalesByStoreId(
            @Param("storeId") UUID storeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
