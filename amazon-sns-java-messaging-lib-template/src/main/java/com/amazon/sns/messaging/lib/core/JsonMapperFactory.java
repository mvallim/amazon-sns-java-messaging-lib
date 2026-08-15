/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.sns.messaging.lib.core;

import java.util.Objects;

import org.apache.fory.json.ForyJson;

import com.amazon.sns.messaging.lib.exception.PoisonRequestEntryException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

/**
 * Factory for creating {@link JsonMapper} instances backed by a specific JSON
 * serialization library.
 *
 * <p>
 * Each {@code create} overload wraps a caller-supplied JSON library delegate
 * ({@link ObjectMapper} or {@link ForyJson}) in a {@link JsonMapper}
 * implementation that translates any failure raised by the delegate into a
 * {@link PoisonRequestEntryException}, giving callers a single,
 * library-agnostic exception type to handle regardless of which JSON library
 * backs the returned mapper.
 *
 * <p>
 * This class is not instantiable; use the static {@code create} methods to
 * obtain a {@link JsonMapper}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JsonMapperFactory {

  /**
   * Creates a {@link JsonMapper} backed by the given Jackson
   * {@link ObjectMapper}.
   *
   * @param objectMapper the Jackson mapper to delegate serialization and
   *                     deserialization to; must not be {@code null}
   * @return a {@link JsonMapper} that delegates to {@code objectMapper}
   * @throws NullPointerException if {@code objectMapper} is {@code null}
   */
  static JsonMapper create(final ObjectMapper objectMapper) {
    return new JsonMapperJackson(Objects.requireNonNull(objectMapper, "objectMapper cannot be null"));
  }

  /**
   * Creates a {@link JsonMapper} backed by the given Apache Fory
   * {@link ForyJson}.
   *
   * @param foryJson the Fory JSON mapper to delegate serialization and
   *                 deserialization to; must not be {@code null}
   * @return a {@link JsonMapper} that delegates to {@code foryJson}
   * @throws NullPointerException if {@code foryJson} is {@code null}
   */
  static JsonMapper create(final ForyJson foryJson) {
    return new JsonMapperFory(Objects.requireNonNull(foryJson, "foryJson cannot be null"));
  }

  /**
   * {@link JsonMapper} implementation backed by a Jackson {@link ObjectMapper}.
   *
   * <p>
   * Every operation delegates to the wrapped {@code ObjectMapper} and translates
   * any exception raised during serialization or deserialization into a
   * {@link PoisonRequestEntryException}, preserving the original exception's
   * message and cause.
   */
  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  static class JsonMapperJackson implements JsonMapper {

    private final ObjectMapper objectMapper;

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ObjectMapper}
     *                                     fails to serialize {@code value}
     */
    @Override
    public String toJson(final Object value) throws PoisonRequestEntryException {
      try {
        return objectMapper.writeValueAsString(value);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ObjectMapper}
     *                                     fails to serialize {@code value}
     */
    @Override
    public byte[] toJsonBytes(final Object value) throws PoisonRequestEntryException {
      try {
        return objectMapper.writeValueAsBytes(value);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ObjectMapper}
     *                                     fails to deserialize {@code json} into an
     *                                     instance of {@code clazz}
     */
    @Override
    public <T> T fromJson(final String json, final Class<T> clazz) throws PoisonRequestEntryException {
      try {
        return objectMapper.readValue(json, clazz);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ObjectMapper}
     *                                     fails to deserialize {@code bytes} into
     *                                     an instance of {@code clazz}
     */
    @Override
    public <T> T fromJson(final byte[] bytes, final Class<T> clazz) throws PoisonRequestEntryException {
      try {
        return objectMapper.readValue(bytes, clazz);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

  }

  /**
   * {@link JsonMapper} implementation backed by an Apache Fory {@link ForyJson}.
   *
   * <p>
   * Every operation delegates to the wrapped {@code ForyJson} and translates any
   * exception raised during serialization or deserialization into a
   * {@link PoisonRequestEntryException}, preserving the original exception's
   * message and cause.
   */
  @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
  static class JsonMapperFory implements JsonMapper {

    private final ForyJson foryJson;

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ForyJson} fails
     *                                     to serialize {@code value}
     */
    @Override
    public String toJson(final Object value) throws PoisonRequestEntryException {
      try {
        return foryJson.toJson(value);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ForyJson} fails
     *                                     to serialize {@code value}
     */
    @Override
    public byte[] toJsonBytes(final Object value) throws PoisonRequestEntryException {
      try {
        return foryJson.toJsonBytes(value);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ForyJson} fails
     *                                     to deserialize {@code json} into an
     *                                     instance of {@code clazz}
     */
    @Override
    public <T> T fromJson(final String json, final Class<T> clazz) throws PoisonRequestEntryException {
      try {
        return foryJson.fromJson(json, clazz);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

    /**
     * {@inheritDoc}
     *
     * @throws PoisonRequestEntryException if the underlying {@link ForyJson} fails
     *                                     to deserialize {@code bytes} into an
     *                                     instance of {@code clazz}
     */
    @Override
    public <T> T fromJson(final byte[] bytes, final Class<T> clazz) throws PoisonRequestEntryException {
      try {
        return foryJson.fromJson(bytes, clazz);
      } catch (final Exception ex) {
        throw PoisonRequestEntryException.fromJsonProcessing(ex.getMessage(), ex);
      }
    }

  }

}