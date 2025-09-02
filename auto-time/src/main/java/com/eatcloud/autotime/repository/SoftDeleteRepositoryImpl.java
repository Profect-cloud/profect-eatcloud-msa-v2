package com.eatcloud.autotime.repository;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;

import com.eatcloud.autotime.BaseTimeEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

public class SoftDeleteRepositoryImpl<T extends BaseTimeEntity, ID extends Serializable>
	extends SimpleJpaRepository<T, ID>
	implements SoftDeleteRepository<T, ID> {

	private final EntityManager em;
	private final JpaEntityInformation<T, ?> info;

	public SoftDeleteRepositoryImpl(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
		super(entityInformation, entityManager);
		this.em = entityManager;
		this.info = entityInformation;
	}

	@Override public void delete(T e) { throw unsupported(); }
	@Override public void deleteAll(Iterable<? extends T> e) { throw unsupported(); }
	@Override public void deleteAll() { throw unsupported(); }
	@Override public void deleteById(ID id) { throw unsupported(); }
	private static UnsupportedOperationException unsupported() {
		return new UnsupportedOperationException("Use softDelete*() instead of physical delete");
	}

	@Override
	public void softDelete(T entity, String actor) {
		Objects.requireNonNull(entity); Objects.requireNonNull(actor);
		entity.markDeleted(LocalDateTime.now(), actor);
		super.save(entity);
	}

	@Override
	public void softDeleteById(ID id, String actor) {
		Objects.requireNonNull(id); Objects.requireNonNull(actor);
		Optional<T> opt = super.findById(id);
		opt.ifPresent(e -> softDelete(e, actor));
	}

	@Override
	public int softDeleteAllByIds(Collection<ID> ids, String actor) {
		if (ids == null || ids.isEmpty()) return 0;
		var now = LocalDateTime.now();
		String entity = info.getEntityName();
		String idProp = (info.getIdAttribute() != null) ? info.getIdAttribute().getName() : "id";
		String jpql = String.format(
			"UPDATE %s e SET e.deletedAt = :now, e.deletedBy = :actor WHERE e.%s IN :ids AND e.deletedAt IS NULL",
			entity, idProp
		);
		Query q = em.createQuery(jpql);
		q.setParameter("now", now);
		q.setParameter("actor", actor);
		q.setParameter("ids", ids);
		int updated = q.executeUpdate();
		em.clear();
		return updated;
	}

	@Override
	public int restoreAllByIds(Collection<ID> ids) {
		if (ids == null || ids.isEmpty()) return 0;
		String entity = info.getEntityName();
		String idProp = (info.getIdAttribute() != null) ? info.getIdAttribute().getName() : "id";
		String jpql = String.format(
			"UPDATE %s e SET e.deletedAt = NULL, e.deletedBy = NULL WHERE e.%s IN :ids AND e.deletedAt IS NOT NULL",
			entity, idProp
		);
		Query q = em.createQuery(jpql);
		q.setParameter("ids", ids);
		int updated = q.executeUpdate();
		em.clear();
		return updated;
	}
}
