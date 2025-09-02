package com.eatcloud.adminservice.adapters;

import com.eatcloud.adminservice.domain.admin.dto.StoreDto;
import com.eatcloud.adminservice.domain.admin.exception.*;
import com.eatcloud.adminservice.ports.StoreDirectoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreDirectoryRestAdapter implements StoreDirectoryPort {

    private final RestClient storeDirectoryRestClient;

    @Override
    public List<StoreDto> findAll() {
        try {
            StoreDto[] arr = storeDirectoryRestClient.get()
                    .uri("/internal/admin/stores")
                    .retrieve()
                    .body(StoreDto[].class);
            return arr == null ? List.of() : Arrays.asList(arr);
        } catch (Exception e) {
            log.error("store-service findAll 실패", e);
            throw new AdminException(AdminErrorCode.STORE_SERVICE_FAILED, e);
        }
    }

    @Override
    public StoreDto getById(UUID storeId) {
        try {
            return storeDirectoryRestClient.get()
                    .uri("/internal/admin/stores/{id}", storeId)
                    .retrieve()
                    .onStatus(s -> s.value()==404, (req,res) -> new AdminException(AdminErrorCode.STORE_NOT_FOUND))
                    .body(StoreDto.class);
        } catch (AdminException ae) { throw ae; }
        catch (Exception e) {
            log.error("store-service getById 실패: {}", storeId, e);
            throw new AdminException(AdminErrorCode.STORE_SERVICE_FAILED, e);
        }
    }

    @Override
    public void softDeleteById(UUID storeId) {
        try {
            storeDirectoryRestClient.delete()
                    .uri("/internal/admin/stores/{id}", storeId)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req,res) -> new AdminException(AdminErrorCode.STORE_SERVICE_FAILED))
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("store-service deleteById 실패: {}", storeId, e);
            throw new AdminException(AdminErrorCode.STORE_SERVICE_FAILED, e);
        }
    }
}
