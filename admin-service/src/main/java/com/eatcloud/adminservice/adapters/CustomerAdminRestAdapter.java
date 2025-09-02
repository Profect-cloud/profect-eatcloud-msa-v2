// com.eatcloud.adminservice.adapters.CustomerAdminRestAdapter
package com.eatcloud.adminservice.adapters;

import com.eatcloud.adminservice.domain.admin.dto.UserDto;
import com.eatcloud.adminservice.domain.admin.exception.*;
import com.eatcloud.adminservice.ports.CustomerAdminPort;
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
public class CustomerAdminRestAdapter implements CustomerAdminPort {

    private final RestClient customerAdminRestClient;

    @Override
    public List<UserDto> findAll() {
        try {
            UserDto[] arr = customerAdminRestClient.get()
                    .uri("/internal/admin/customers")
                    .retrieve()
                    .body(UserDto[].class);
            return arr == null ? List.of() : Arrays.asList(arr);
        } catch (Exception e) {
            log.error("customer-service findAll 실패", e);
            throw new AdminException(AdminErrorCode.CUSTOMER_SERVICE_FAILED, e);
        }
    }

    @Override
    public UserDto getByEmail(String email) {
        try {
            return customerAdminRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/admin/customers/search")
                            .queryParam("email", email).build())
                    .retrieve()
                    .onStatus(s -> s.value()==404, (req,res) -> new AdminException(AdminErrorCode.CUSTOMER_NOT_FOUND))
                    .body(UserDto.class);
        } catch (AdminException ae) { throw ae; }
        catch (Exception e) {
            log.error("customer-service getByEmail 실패: {}", email, e);
            throw new AdminException(AdminErrorCode.CUSTOMER_SERVICE_FAILED, e);
        }
    }

    @Override
    public void softDeleteByEmail(String email) {
        try {
            customerAdminRestClient.delete()
                    .uri(uriBuilder -> uriBuilder.path("/internal/admin/customers/by-email")
                            .queryParam("email", email).build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req,res) -> new AdminException(AdminErrorCode.CUSTOMER_SERVICE_FAILED))
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("customer-service deleteByEmail 실패: {}", email, e);
            throw new AdminException(AdminErrorCode.CUSTOMER_SERVICE_FAILED, e);
        }
    }
}
