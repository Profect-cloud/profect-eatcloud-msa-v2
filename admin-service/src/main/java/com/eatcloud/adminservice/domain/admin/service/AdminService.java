package com.eatcloud.adminservice.domain.admin.service;

import com.eatcloud.adminservice.domain.admin.dto.*;
import com.eatcloud.adminservice.domain.admin.entity.Admin;
import com.eatcloud.adminservice.domain.admin.repository.AdminRepository;
import com.eatcloud.adminservice.ports.CustomerAdminPort;
import com.eatcloud.adminservice.ports.ManagerDirectoryPort;
import com.eatcloud.adminservice.ports.StoreDirectoryPort;
import com.eatcloud.logging.annotation.Loggable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true)
public class AdminService {

	private final CustomerAdminPort customerPort;
	private final ManagerDirectoryPort managerPort;
	private final StoreDirectoryPort storePort;
	private final AdminRepository adminRepository;

	// ============ Customers ============
	public List<UserDto> getAllCustomers() {
		return customerPort.findAll();
	}

	public UserDto getCustomerByEmail(String email) {
		return customerPort.getByEmail(email);
	}

	public void deleteCustomerByEmail(String email) {
		customerPort.softDeleteByEmail(email);
	}

	// ============ Managers ============
	public List<ManagerDto> getAllManagers() {
		return managerPort.findAll();
	}

	public ManagerDto getManagerByEmail(String email) {
		return managerPort.getByEmail(email);
	}

	public void deleteManagerByEmail(String email) {
		managerPort.softDeleteByEmail(email);
	}

	// ============ Stores ============
	public List<StoreDto> getStores() {
		return storePort.findAll();
	}

	public StoreDto getStore(UUID storeId) {
		return storePort.getById(storeId);
	}

	public void deleteStore(UUID storeId) {
		storePort.softDeleteById(storeId);
	}

	public UserLoginDto findByEmail(String email) {
		Admin admin = adminRepository.findByEmail(email)
				.orElseThrow(() -> new RuntimeException("Admin not found"));

		return UserLoginDto.builder()
				.id(admin.getId())
				.email(admin.getEmail())
				.password(admin.getPassword())
				.name(admin.getName())
				.role("admin")
				.build();
	}
}
