package com.amazon.sns.messaging.lib.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.fory.json.ForyJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.exception.PoisonRequestEntryException;
import com.fasterxml.jackson.databind.ObjectMapper;

class JsonMapperFactoryTest {

  private static final String SAMPLE_JSON = "{\"key\":\"value\"}";

  private static final byte[] SAMPLE_JSON_BYTES = SAMPLE_JSON.getBytes(StandardCharsets.UTF_8);

  private static final Object SAMPLE_VALUE = new Object();

  @Nested
  @DisplayName("static create(...) factory methods")
  class CreateFactoryMethods {

    @Test
    @DisplayName("create(ObjectMapper) throws NPE with descriptive message when null")
    void testCreateJacksonNullObjectMapperThrowsNullPointerException() {
      final NullPointerException ex = assertThrows(NullPointerException.class, () -> JsonMapperFactory.create((ObjectMapper) null));
      assertThat(ex.getMessage(), is("objectMapper cannot be null"));
    }

    @Test
    @DisplayName("create(ObjectMapper) returns a non-null JsonMapper for a valid delegate")
    void testCreateJacksonValidObjectMapperReturnsMapper() {
      final ObjectMapper objectMapper = mock(ObjectMapper.class);

      final JsonMapper mapper = JsonMapperFactory.create(objectMapper);

      assertThat(mapper, is(notNullValue()));
      assertThat(mapper, instanceOf(JsonMapperFactory.JsonMapperJackson.class));
    }

    @Test
    @DisplayName("create(ForyJson) throws NPE with descriptive message when null")
    void testCreateForyNullForyJsonThrowsNullPointerException() {
      final NullPointerException ex = assertThrows(NullPointerException.class, () -> JsonMapperFactory.create((ForyJson) null));
      assertThat(ex.getMessage(), is("foryJson cannot be null"));
    }

    @Test
    @DisplayName("create(ForyJson) returns a non-null JsonMapper for a valid delegate")
    void testCreateForyValidForyJsonReturnsMapper() {
      final ForyJson foryJson = mock(ForyJson.class);

      final JsonMapper mapper = JsonMapperFactory.create(foryJson);

      assertThat(mapper, is(notNullValue()));
      assertThat(mapper, instanceOf(JsonMapperFactory.JsonMapperFory.class));
    }
  }

  @Nested
  @DisplayName("JsonMapperJackson")
  class JsonMapperJacksonTest {

    private ObjectMapper objectMapper;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
      objectMapper = mock(ObjectMapper.class);
      jsonMapper = new JsonMapperFactory.JsonMapperJackson(objectMapper);
    }

    @Test
    void testToJsonDelegatesAndReturnsResult() throws Exception {
      when(objectMapper.writeValueAsString(SAMPLE_VALUE)).thenReturn(SAMPLE_JSON);

      final String result = jsonMapper.toJson(SAMPLE_VALUE);

      assertThat(result, is(SAMPLE_JSON));
      verify(objectMapper).writeValueAsString(SAMPLE_VALUE);
    }

    @Test
    void testToJsonDelegateThrowsWrapsInPoisonRequestEntryException() throws Exception {
      final IOException cause = new IOException("boom");
      when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> { throw cause; });

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.toJson(SAMPLE_VALUE));

      assertThat(ex.getCause(), sameInstance(cause));
    }

    @Test
    void testToJsonBytesDelegatesAndReturnsResult() throws Exception {
      when(objectMapper.writeValueAsBytes(SAMPLE_VALUE)).thenReturn(SAMPLE_JSON_BYTES);

      final byte[] result = jsonMapper.toJsonBytes(SAMPLE_VALUE);

      assertThat(result, is(SAMPLE_JSON_BYTES));
      verify(objectMapper).writeValueAsBytes(SAMPLE_VALUE);
    }

    @Test
    void testToJsonBytesDelegateThrowsWrapsInPoisonRequestEntryException() throws Exception {
      final IOException cause = new IOException("boom");
      when(objectMapper.writeValueAsBytes(any())).thenAnswer(invocation -> { throw cause; });

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.toJsonBytes(SAMPLE_VALUE));

      assertThat(ex.getCause(), sameInstance(cause));
    }

    @Test
    void testFromJsonStringDelegatesAndReturnsResult() throws Exception {
      when(objectMapper.readValue(eq(SAMPLE_JSON), eq(String.class))).thenReturn("value");

      final String result = jsonMapper.fromJson(SAMPLE_JSON, String.class);

      assertThat(result, is("value"));
      verify(objectMapper).readValue(SAMPLE_JSON, String.class);
    }

    @Test
    void testFromJsonStringDelegateThrowsWrapsInPoisonRequestEntryException() throws Exception {
      final IOException cause = new IOException("malformed");
      when(objectMapper.readValue(any(String.class), eq(String.class))).thenAnswer(invocation -> { throw cause; });

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.fromJson(SAMPLE_JSON, String.class));

      assertThat(ex.getCause(), sameInstance(cause));
    }

    @Test
    void testFromJsonBytesDelegatesAndReturnsResult() throws Exception {
      when(objectMapper.readValue(eq(SAMPLE_JSON_BYTES), eq(String.class))).thenReturn("value");

      final String result = jsonMapper.fromJson(SAMPLE_JSON_BYTES, String.class);

      assertThat(result, is("value"));
      verify(objectMapper).readValue(SAMPLE_JSON_BYTES, String.class);
    }

    @Test
    void testFromJsonBytesDelegateThrowsWrapsInPoisonRequestEntryException() throws Exception {
      final IOException cause = new IOException("malformed");
      when(objectMapper.readValue(any(byte[].class), eq(String.class))).thenThrow(cause);

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.fromJson(SAMPLE_JSON_BYTES, String.class));

      assertThat(ex.getCause(), sameInstance(cause));
    }
  }

  @Nested
  @DisplayName("JsonMapperFory")
  class JsonMapperForyTest {

    private ForyJson foryJson;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() {
      foryJson = mock(ForyJson.class);
      jsonMapper = new JsonMapperFactory.JsonMapperFory(foryJson);
    }

    @Test
    void testToJsonDelegatesAndReturnsResult() throws Exception {
      when(foryJson.toJson(SAMPLE_VALUE)).thenReturn(SAMPLE_JSON);

      final String result = jsonMapper.toJson(SAMPLE_VALUE);

      assertThat(result, is(SAMPLE_JSON));
      verify(foryJson).toJson(SAMPLE_VALUE);
    }

    @Test
    void testToJsonDelegateThrowsWrapsInPoisonRequestEntryException() {
      final RuntimeException cause = new RuntimeException("boom");
      doThrow(cause).when(foryJson).toJson(any());

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.toJson(SAMPLE_VALUE));

      assertThat(ex.getCause(), sameInstance(cause));
    }

    @Test
    void testToJsonBytesDelegatesAndReturnsResult() throws Exception {
      when(foryJson.toJsonBytes(SAMPLE_VALUE)).thenReturn(SAMPLE_JSON_BYTES);

      final byte[] result = jsonMapper.toJsonBytes(SAMPLE_VALUE);

      assertThat(result, is(SAMPLE_JSON_BYTES));
      verify(foryJson).toJsonBytes(SAMPLE_VALUE);
    }

    @Test
    void testToJsonBytesDelegateThrowsWrapsInPoisonRequestEntryException() {
      final RuntimeException cause = new RuntimeException("boom");
      doThrow(cause).when(foryJson).toJsonBytes(any());

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.toJsonBytes(SAMPLE_VALUE));

      assertThat(ex.getCause(), sameInstance(cause));
    }

    @Test
    void testFromJsonStringDelegatesAndReturnsResult() throws Exception {
      when(foryJson.fromJson(eq(SAMPLE_JSON), eq(String.class))).thenReturn("value");

      final String result = jsonMapper.fromJson(SAMPLE_JSON, String.class);

      assertThat(result, is("value"));
      verify(foryJson).fromJson(SAMPLE_JSON, String.class);
    }

    @Test
    void testFromJsonStringDelegateThrowsWrapsInPoisonRequestEntryException() {
      final RuntimeException cause = new RuntimeException("malformed");
      doThrow(cause).when(foryJson).fromJson(any(String.class), eq(String.class));

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.fromJson(SAMPLE_JSON, String.class));

      assertThat(ex.getCause(), sameInstance(cause));
    }

    @Test
    void testFromJsonBytesDelegatesAndReturnsResult() throws Exception {
      when(foryJson.fromJson(eq(SAMPLE_JSON_BYTES), eq(String.class))).thenReturn("value");

      final String result = jsonMapper.fromJson(SAMPLE_JSON_BYTES, String.class);

      assertThat(result, is("value"));
      verify(foryJson).fromJson(SAMPLE_JSON_BYTES, String.class);
    }

    @Test
    void testFromJsonBytesDelegateThrowsWrapsInPoisonRequestEntryException() {
      final RuntimeException cause = new RuntimeException("malformed");
      doThrow(cause).when(foryJson).fromJson(any(byte[].class), eq(String.class));

      final PoisonRequestEntryException ex = assertThrows(PoisonRequestEntryException.class, () -> jsonMapper.fromJson(SAMPLE_JSON_BYTES, String.class));

      assertThat(ex.getCause(), sameInstance(cause));
    }
  }
}