package com.amazon.sns.messaging.helper;

import java.util.function.Consumer;

public class ConsumerTest<T> implements Consumer<T> {

  private final Consumer<T> assertThat;

  public ConsumerTest(final Consumer<T> assertThat) {
    this.assertThat = assertThat;
  }

  @Override
  public void accept(final T result) {
    assertThat.accept(result);
  }

}