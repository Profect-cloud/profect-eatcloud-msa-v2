package com.eatcloud.storeservice.domain.store.repository;

import com.eatcloud.storeservice.domain.menu.entity.QMenu;
import com.eatcloud.storeservice.domain.store.dto.StoreKeywordSearchRequestDto;
import com.eatcloud.storeservice.domain.store.dto.StoreSearchResponseDto;
import com.eatcloud.storeservice.domain.store.entity.QStore;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class StoreCustomRepositoryImpl implements StoreCustomRepository {

    @PersistenceContext
    private EntityManager em;

    private final JPAQueryFactory query;

    public StoreCustomRepositoryImpl(JPAQueryFactory query) {
        this.query = query;
    }

    @Override
    public List<StoreSearchResponseDto> findStoresByCategoryWithinDistance(UUID categoryId, double lat, double lon, double distanceKm) {
        String sql = """
                SELECT 
                    s.store_id,
                    s.store_name,
                    s.store_address,
                    s.store_lat,
                    s.store_lon,
                    s.min_cost,
                    s.open_status
                FROM p_stores s
                WHERE s.store_category_id = :categoryId
                  AND ST_DWithin(
                        geography(ST_MakePoint(s.store_lon, s.store_lat)),
                        geography(ST_MakePoint(:lon, :lat)),
                        :distanceMeters
                  )
                  AND s.open_status = true
            """;

        List<Object[]> resultList = em.createNativeQuery(sql)
                .setParameter("categoryId", categoryId)
                .setParameter("lat", lat)
                .setParameter("lon", lon)
                .setParameter("distanceMeters", distanceKm * 1000)
                .getResultList();

        return resultList.stream()
                .map(row -> StoreSearchResponseDto.of(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (Double) row[3],
                        (Double) row[4],
                        (Integer) row[5],
                        (Boolean) row[6]
                ))
                .toList();
    }

    @Override
    public List<StoreSearchResponseDto> findStoresByMenuCategoryWithinDistance(
            String menuCategoryCode, double userLat, double userLon, double distanceKm) {

        String sql = """
        SELECT 
            s.store_id,
            s.store_name,
            s.store_address,
            s.store_lat,
            s.store_lon,
            s.min_cost,
            s.open_status
        FROM p_stores s
        JOIN p_menus m ON m.store_id = s.store_id
        WHERE ST_DistanceSphere(
            ST_MakePoint(s.store_lon, s.store_lat),
            ST_MakePoint(:userLon, :userLat)
        ) <= (:distanceKm * 1000)
        AND m.menu_category_code = :menuCategoryCode
        AND s.open_status = true
        GROUP BY s.store_id, s.store_name, s.store_address, s.store_lat, s.store_lon, s.min_cost, s.open_status
    """;

        List<Object[]> resultList = em.createNativeQuery(sql)
                .setParameter("userLat", userLat)
                .setParameter("userLon", userLon)
                .setParameter("distanceKm", distanceKm)
                .setParameter("menuCategoryCode", menuCategoryCode)
                .getResultList();

        return resultList.stream()
                .map(row -> StoreSearchResponseDto.of(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (Double) row[3],
                        (Double) row[4],
                        (Integer) row[5],
                        (Boolean) row[6]
                ))
                .toList();
    }

    @Override
    public Page<StoreSearchResponseDto> searchByKeywordAndCategory(
            StoreKeywordSearchRequestDto req, Pageable pageable) {

        QStore s = QStore.store;
        QMenu  m = QMenu.menu;

        BooleanBuilder where = new BooleanBuilder();

        if (req.getQ() != null && !req.getQ().isBlank()) {
            String like = "%" + req.getQ() + "%";
            where.and(
                    s.storeName.likeIgnoreCase(like)
                            .or(s.description.likeIgnoreCase(like))
                            .or(m.menuName.likeIgnoreCase(like))
            );
        }

        if (req.getStoreCategoryId() != null) {
            // TODO: Store 엔티티에 상위 카테고리 컬럼명이 다르면 수정
            where.and(s.storeCategoryId.eq(req.getStoreCategoryId()));
        }

        if (req.getMenuCategoryCode() != null && !req.getMenuCategoryCode().isBlank()) {
            where.and(m.menuCategoryCode.eq(req.getMenuCategoryCode()));
        }

        OrderSpecifier<?>[] orderSpecifiers = toOrderSpecifiers(pageable, s);

        List<StoreSearchResponseDto> content = query
                .select(Projections.bean(
                        StoreSearchResponseDto.class,
                        s.storeId.as("storeId"),
                        s.storeName.as("storeName"),
                        s.storeAddress.as("storeAddress"),
                        s.storeLat.as("storeLat"),
                        s.storeLon.as("storeLon"),
                        s.minCost.as("minCost"),
                        s.openStatus.as("openStatus")
                ))
                .from(s)
                .leftJoin(m).on(m.store.storeId.eq(s.storeId))
                .where(where)
                .groupBy(s.storeId, s.storeName, s.storeAddress, s.storeLat, s.storeLon, s.minCost, s.openStatus)
                .orderBy(orderSpecifiers)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        Long total = query
                .select(s.storeId.countDistinct())
                .from(s)
                .leftJoin(m).on(m.store.storeId.eq(s.storeId))
                .where(where)
                .fetchOne();

        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    private OrderSpecifier<?>[] toOrderSpecifiers(Pageable pageable, QStore s) {
        List<OrderSpecifier<?>> list = new ArrayList<>();

        if (pageable.getSort().isEmpty()) {
            list.add(s.storeName.asc());
            list.add(s.storeId.desc()); // tie-breaker
            return list.toArray(OrderSpecifier[]::new);
        }

        for (Sort.Order o : pageable.getSort()) {
            Order dir = o.isAscending() ? Order.ASC : Order.DESC;
            switch (o.getProperty()) {
                case "storeName" -> list.add(new OrderSpecifier<>(dir, s.storeName));
                case "minCost"   -> list.add(new OrderSpecifier<>(dir, s.minCost));
                case "openStatus"-> list.add(new OrderSpecifier<>(dir, s.openStatus));
                case "storeId"   -> list.add(new OrderSpecifier<>(dir, s.storeId));
                default -> { /* 미지원 정렬키는 무시 */ }
            }
        }

        list.add(s.storeId.desc());
        return list.toArray(OrderSpecifier[]::new);
    }




}
