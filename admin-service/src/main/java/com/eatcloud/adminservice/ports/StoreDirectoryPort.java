// 스토어 조회/삭제 포트 (생성/폐업은 기존 StoreAdminPort 사용)
package com.eatcloud.adminservice.ports;

import com.eatcloud.adminservice.domain.admin.dto.StoreDto;
import java.util.List;
import java.util.UUID;

public interface StoreDirectoryPort {
    List<StoreDto> findAll();
    StoreDto getById(UUID storeId);
    void softDeleteById(UUID storeId);
}
