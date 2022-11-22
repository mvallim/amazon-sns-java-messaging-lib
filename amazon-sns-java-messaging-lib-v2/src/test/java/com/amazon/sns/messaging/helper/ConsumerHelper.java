package com.amazon.sns.messaging.helper;

import java.util.function.Consumer;

public class ConsumerHelper<T> implements Consumer<T> {

  private final Consumer<T> assertThat;

  public ConsumerHelper(final Consumer<T> assertThat) {
    this.assertThat = assertThat;
  }

  @Override
  public void accept(final T result) {
    assertThat.accept(result);
  }

}