/*
 * Copyright and licensing information for this source code can be found in
 * the NOTICE and LICENSE files distributed with this project.
 */

package org.springframework.graphql.data.method.annotation.support;

import graphql.schema.FieldCoordinates;
import org.jspecify.annotations.Nullable;

import org.springframework.graphql.data.method.HandlerMethod;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;


/**
 * Mapping information for a controller method to be registered as a
 * {@link graphql.schema.DataFetcher}.
 *
 * @author Rossen Stoyanchev
 * @since 1.3.0
 */
public final class DataFetcherMappingInfo {

	private final FieldCoordinates coordinates;

	private final boolean batchMapping;

	private final int maxBatchSize;

	private final HandlerMethod handlerMethod;


	public DataFetcherMappingInfo(
			String typeName, String field, boolean batchMapping, int maxBatchSize,
			HandlerMethod handlerMethod) {

		this.coordinates = FieldCoordinates.coordinates(typeName, field);
		this.batchMapping = batchMapping;
		this.maxBatchSize = maxBatchSize;
		this.handlerMethod = handlerMethod;
	}

	public DataFetcherMappingInfo(String typeName, DataFetcherMappingInfo info) {
		this.coordinates = FieldCoordinates.coordinates(typeName, info.getCoordinates().getFieldName());
		this.batchMapping = info.batchMapping;
		this.maxBatchSize = info.maxBatchSize;
		this.handlerMethod = info.handlerMethod;
	}


	/**
	 * The field to bind the controller method to.
	 */
	public FieldCoordinates getCoordinates() {
		return this.coordinates;
	}

	/**
	 * Shortcut for the typeName from the coordinates.
	 */
	public String getTypeName() {
		return this.coordinates.getTypeName();
	}


	/**
	 * Shortcut for the fieldName from the coordinates.
	 */
	public String getFieldName() {
		return this.coordinates.getFieldName();
	}

	/**
	 * Whether it is an {@link BatchMapping} method or not in which case it is
	 * an {@link SchemaMapping} method.
	 */
	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean isBatchMapping() {
		return this.batchMapping;
	}

	/**
	 * A batch size limit to apply for a batch mapping method, or -1 if a limit
	 * does not apply.
	 */
	public int getMaxBatchSize() {
		return this.maxBatchSize;
	}

	/**
	 * The controller method to use for data fetching.
	 */
	public HandlerMethod getHandlerMethod() {
		return this.handlerMethod;
	}

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DataFetcherMappingInfo otherInfo)) {
			return false;
		}
		return (this.coordinates.equals(otherInfo.coordinates));
	}

	@Override
	public int hashCode() {
		return getCoordinates().hashCode() * 31;
	}

	@Override
	public String toString() {
		return this.coordinates + " -> " + getHandlerMethod();
	}




}
