package com.eatcloud.autotime;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

@Getter
@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	protected LocalDateTime createdAt;

	@CreatedBy
	@Column(name = "created_by", nullable = false, updatable = false, length = 100)
	protected String createdBy;

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	protected LocalDateTime updatedAt;

	@LastModifiedBy
	@Column(name = "updated_by", nullable = false, length = 100)
	protected String updatedBy;

	@Column(name = "deleted_at")
	protected LocalDateTime deletedAt;

	@Column(name = "deleted_by", length = 100)
	protected String deletedBy;


	public boolean isDeleted() { return deletedAt != null; }

	public final void markDeleted(LocalDateTime when, String actor) {
		this.deletedAt = when;
		this.deletedBy = actor;
	}
	public final void restore() {
		this.deletedAt = null;
		this.deletedBy = null;
	}
}
