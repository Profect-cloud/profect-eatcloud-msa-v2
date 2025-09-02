package com.eatcloud.adminservice.adapters;

import com.eatcloud.adminservice.domain.admin.dto.ManagerDto;
import com.eatcloud.adminservice.domain.admin.exception.*;
import com.eatcloud.adminservice.ports.ManagerDirectoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ManagerDirectoryRestAdapter implements ManagerDirectoryPort {

    private final RestClient managerDirectoryRestClient;

    @Override
    public List<ManagerDto> findAll() {
        try {
            ManagerDto[] arr = managerDirectoryRestClient.get()
                    .uri("/internal/admin/managers")
                    .retrieve()
                    .body(ManagerDto[].class);
            return arr == null ? List.of() : Arrays.asList(arr);
        } catch (Exception e) {
            log.error("manager-service findAll 실패", e);
            throw new AdminException(AdminErrorCode.MANAGER_SERVICE_FAILED, e);
        }
    }

    @Override
    public ManagerDto getByEmail(String email) {
        try {
            return managerDirectoryRestClient.get()
                    .uri(uri -> uri.path("/internal/admin/managers/search")
                            .queryParam("email", email).build())
                    .retrieve()
                    .onStatus(s -> s.value()==404, (req,res) -> new AdminException(AdminErrorCode.MANAGER_NOT_FOUND))
                    .body(ManagerDto.class);
        } catch (AdminException ae) { throw ae; }
        catch (Exception e) {
            log.error("manager-service getByEmail 실패: {}", email, e);
            throw new AdminException(AdminErrorCode.MANAGER_SERVICE_FAILED, e);
        }
    }

    @Override
    public void softDeleteByEmail(String email) {
        try {
            managerDirectoryRestClient.delete()
                    .uri(uri -> uri.path("/internal/admin/managers/by-email")
                            .queryParam("email", email).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req,res) -> new AdminException(AdminErrorCode.MANAGER_SERVICE_FAILED))
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("manager-service deleteByEmail 실패: {}", email, e);
            throw new AdminException(AdminErrorCode.MANAGER_SERVICE_FAILED, e);
        }
    }
}
