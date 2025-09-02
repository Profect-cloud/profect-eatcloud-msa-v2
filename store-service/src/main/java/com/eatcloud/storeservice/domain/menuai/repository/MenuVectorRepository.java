package com.eatcloud.storeservice.domain.menuai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.eatcloud.storeservice.domain.menuai.entity.MenuVector;

public interface MenuVectorRepository extends JpaRepository<MenuVector, Long> {

	Optional<MenuVector> findByMenuName(String menuName);

	@Query("SELECT mv FROM MenuVector mv")
	List<MenuVector> findAllVectors();

	void deleteByMenuName(String menuName);
}
