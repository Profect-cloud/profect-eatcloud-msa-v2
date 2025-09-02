package com.eatcloud.storeservice.domain.menu.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class DailyMenuSalesId implements Serializable {
    private LocalDate saleDate;
    private UUID storeId;
    private UUID menuId;
}