package com.eatcloud.adminservice.ports;
// 조회 + 삭제까지 포함한 고객 포트
import com.eatcloud.adminservice.domain.admin.dto.UserDto;
import java.util.List;

public interface CustomerAdminPort {
    List<UserDto> findAll();
    UserDto getByEmail(String email);
    void softDeleteByEmail(String email);
}
