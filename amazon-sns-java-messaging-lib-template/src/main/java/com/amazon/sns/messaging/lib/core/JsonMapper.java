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

import com.amazon.sns.messaging.lib.exception.PoisonRequestEntryException;

/**
 * Abstraction over a JSON serialization library used to convert arbitrary Java
 * objects to and from their JSON representation.
 *
 * <p>
 * Implementations are expected to translate any underlying serialization or
 * deserialization failure into a {@link PoisonRequestEntryException}, allowing
 * callers to handle malformed or unprocessable payloads uniformly regardless of
 * which JSON library backs a given implementation.
 *
 * @see JsonMapperFactory
 */
interface JsonMapper {

  /**
   * Serializes the given value to its JSON string representation.
   *
   * @param value the object to serialize; implementations determine whether
   *              {@code null} is permitted
   * @return the JSON representation of {@code value}
   * @throws PoisonRequestEntryException if {@code value} cannot be serialized to
   *                                     JSON
   */
  String toJson(final Object value) throws PoisonRequestEntryException;

  /**
   * Serializes the given value to its JSON representation, encoded as bytes.
   *
   * @param value the object to serialize; implementations determine whether
   *              {@code null} is permitted
   * @return the JSON representation of {@code value}, encoded as bytes
   * @throws PoisonRequestEntryException if {@code value} cannot be serialized to
   *                                     JSON
   */
  byte[] toJsonBytes(final Object value) throws PoisonRequestEntryException;

  /**
   * Deserializes the given JSON string into an instance of the specified type.
   *
   * @param <T>   the target type
   * @param json  the JSON string to deserialize
   * @param clazz the class of the target type
   * @return an instance of {@code T} populated from {@code json}
   * @throws PoisonRequestEntryException if {@code json} is malformed or cannot be
   *                                     deserialized into an instance of
   *                                     {@code clazz}
   */
  <T> T fromJson(final String json, Class<T> clazz) throws PoisonRequestEntryException;

  /**
   * Deserializes the given JSON bytes into an instance of the specified type.
   *
   * @param <T>   the target type
   * @param bytes the JSON content to deserialize, encoded as bytes
   * @param clazz the class of the target type
   * @return an instance of {@code T} populated from {@code bytes}
   * @throws PoisonRequestEntryException if {@code bytes} is malformed or cannot
   *                                     be deserialized into an instance of
   *                                     {@code clazz}
   */
  <T> T fromJson(final byte[] bytes, Class<T> clazz) throws PoisonRequestEntryException;

}