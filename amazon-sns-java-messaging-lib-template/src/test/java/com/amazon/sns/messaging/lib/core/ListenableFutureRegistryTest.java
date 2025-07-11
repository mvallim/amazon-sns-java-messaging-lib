package com.amazon.sns.messaging.lib.core;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import com.amazon.sns.messaging.lib.model.ResponseFailEntry;
import com.amazon.sns.messaging.lib.model.ResponseSuccessEntry;

// @formatter:off
class ListenableFutureRegistryTest {

  @Test
  void testSuccessWithCallbacks() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());
    final Consumer<? super ResponseFailEntry> failureCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(successCallback, failureCallback);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));
    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

  @Test
  void testSuccessWithCallback() {
    final Consumer<? super ResponseSuccessEntry> successCallback = entry -> assertThat(entry, notNullValue());

    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(successCallback);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

  @Test
  void testSuccessWithouCallback() {
    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(null);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

  @Test
  void testSuccessWithouCallbacks() {
    final ListenableFutureRegistry listenableFutureRegistry = new ListenableFutureRegistry();

    listenableFutureRegistry.addCallback(null, null);

    listenableFutureRegistry.success(mock(ResponseSuccessEntry.class));
    listenableFutureRegistry.fail(mock(ResponseFailEntry.class));

    assertThat(listenableFutureRegistry.completable(), notNullValue());
  }

}
// @formatter:on
