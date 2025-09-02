package com.eatcloud.storeservice.domain.store.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class DailyStoreSalesId implements Serializable {
    private LocalDate saleDate;
    private UUID storeId;
}