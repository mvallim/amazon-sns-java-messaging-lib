package com.amazon.sns.messaging.lib.helpers;

@FunctionalInterface
public interface TryConsumer<T> {

  void accept(T t) throws Exception;
}