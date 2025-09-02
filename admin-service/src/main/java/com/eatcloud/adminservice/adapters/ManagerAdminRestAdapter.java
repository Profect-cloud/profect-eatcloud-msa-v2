package com.eatcloud.adminservice.adapters;

import com.eatcloud.adminservice.domain.admin.exception.AdminErrorCode;
import com.eatcloud.adminservice.domain.admin.exception.AdminException;
import com.eatcloud.adminservice.ports.ManagerAdminPort;
import com.eatcloud.adminservice.ports.ManagerUpsertCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerAdminRestAdapter implements ManagerAdminPort {

    private final RestClient managerAdminRestClient;

    @Override
    public UUID upsert(ManagerUpsertCommand cmd) {
        try {
            return managerAdminRestClient.post()
                    .uri("/internal/admin/managers:upsert")
                    .body(cmd)
                    .retrieve()
                    .onStatus(s -> s.value()==400, (req,res) -> new AdminException(AdminErrorCode.MANAGER_SERVICE_FAILED))
                    .onStatus(s -> s.value()==401 || s.value()==403, (req,res) -> new AdminException(AdminErrorCode.UNAUTHORIZED_INTERNAL_CALL))
                    .onStatus(HttpStatusCode::is5xxServerError, (req,res) -> new AdminException(AdminErrorCode.MANAGER_SERVICE_FAILED))
                    .body(UUID.class);
        } catch (Exception e) {
            log.error("manager-service upsert 실패: cmd={}", cmd, e);
            throw new AdminException(AdminErrorCode.MANAGER_SERVICE_FAILED, e);
        }
    }
}
