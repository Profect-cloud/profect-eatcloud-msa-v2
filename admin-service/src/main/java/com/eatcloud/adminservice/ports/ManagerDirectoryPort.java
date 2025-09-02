// 매니저 조회/삭제 포트 (upsert는 기존 ManagerAdminPort 사용)
package com.eatcloud.adminservice.ports;

import com.eatcloud.adminservice.domain.admin.dto.ManagerDto;
import java.util.List;

public interface ManagerDirectoryPort {
    List<ManagerDto> findAll();
    ManagerDto getByEmail(String email);
    void softDeleteByEmail(String email);
}
