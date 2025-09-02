package com.eatcloud.autotime.repository;

import java.io.Serializable;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import com.eatcloud.autotime.BaseTimeEntity;

@NoRepositoryBean
public interface SoftDeleteRepository<T extends BaseTimeEntity, ID extends Serializable>
	extends JpaRepository<T, ID> {

	void softDelete(T entity, String actor);
	void softDeleteById(ID id, String actor);

	int softDeleteAllByIds(Collection<ID> ids, String actor);

	int restoreAllByIds(Collection<ID> ids);

}
