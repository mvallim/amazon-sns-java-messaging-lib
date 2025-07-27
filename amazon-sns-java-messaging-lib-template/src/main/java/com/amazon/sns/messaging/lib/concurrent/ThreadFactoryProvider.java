/*
 * Copyright 2023 the original author or authors.
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

package com.amazon.sns.messaging.lib.concurrent;

import java.lang.reflect.Method;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ThreadFactoryProvider {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(ThreadFactoryProvider.class);
  
  private static Supplier<ThreadFactory> supplierThreadFactory;
  
  static {
    if (ThreadFactoryProvider.getJavaVersion() >= 21) {
      ThreadFactoryProvider.supplierThreadFactory = ThreadFactoryProvider::getVirtualThreadFactory;
      ThreadFactoryProvider.LOGGER.info("Java version is {}, using virtual thread factory", ThreadFactoryProvider.getJavaVersion());
    } else {
      ThreadFactoryProvider.supplierThreadFactory = ThreadFactoryProvider::getDefaultThreadFactory;
      ThreadFactoryProvider.LOGGER.info("Java version is {}, using default thread factory", ThreadFactoryProvider.getJavaVersion());
    }
  }
  
  public static ThreadFactory getThreadFactory() {
    return ThreadFactoryProvider.supplierThreadFactory.get();
  }
  
  @SneakyThrows
  private static ThreadFactory getDefaultThreadFactory() {
    return Executors.defaultThreadFactory();
  }
  
  @SneakyThrows
  private static ThreadFactory getVirtualThreadFactory() {
    final Class<?> clazzThread = Thread.class;
    final Class<?> clazzOfVirtual = Class.forName("java.lang.Thread$Builder$OfVirtual");
    final Method ofVirtualMethod = clazzThread.getMethod("ofVirtual");
    final Method factoryMethod = clazzOfVirtual.getMethod("factory");
    final Object result = ofVirtualMethod.invoke(null);
    return ThreadFactory.class.cast(factoryMethod.invoke(result));
  }

  private static int getJavaVersion() {
    String version = System.getProperty("java.version");
    
    if (version.startsWith("1.")) {
      version = version.substring(2);
    }
    
    final int dotPos = version.indexOf('.');
    final int dashPos = version.indexOf('-');
    final int endIndex = dotPos > -1 ? dotPos : dashPos > -1 ? dashPos : 1;
    
    return Integer.parseInt(version.substring(0, endIndex));
  }
  
}