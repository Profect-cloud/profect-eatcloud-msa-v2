package com.eatcloud.adminservice.adapters;

import com.eatcloud.adminservice.ports.*;
import com.eatcloud.adminservice.domain.admin.exception.AdminErrorCode;
import com.eatcloud.adminservice.domain.admin.exception.AdminException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class StoreAdminRestAdapter implements StoreAdminPort {

    private final RestClient storeAdminRestClient;

    @Override
    public UUID createStore(CreateStoreCommand cmd) {
        try {
            return storeAdminRestClient.post()
                    .uri("/internal/admin/stores")
                    .header("Idempotency-Key", cmd.getApplicationId().toString())
                    .body(cmd)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) ->
                            new AdminException(AdminErrorCode.STORE_SERVICE_FAILED))
                    .body(UUID.class);
        } catch (Exception e) {
            throw new AdminException(AdminErrorCode.STORE_SERVICE_FAILED, e);
        }
    }

    @Override
    public void closeStore(UUID storeId, CloseStoreCommand cmd) {
        try {
            storeAdminRestClient.post()
                    .uri("/internal/admin/stores/{storeId}:close", storeId)
                    .header("Idempotency-Key", cmd.getApplicationId().toString())
                    .body(cmd)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new AdminException(AdminErrorCode.STORE_SERVICE_FAILED, e);
        }
    }
}
