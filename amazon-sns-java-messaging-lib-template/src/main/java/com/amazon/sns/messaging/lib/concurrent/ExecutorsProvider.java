/*
 * Copyright 2024 the original author or authors.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

/**
 * Provides {@link ExecutorService} instances, selecting between virtual thread executors
 * (Java 21+) and default single-thread executors based on the runtime Java version.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExecutorsProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorsProvider.class);

  private static Supplier<ExecutorService> supplierExecutorService;

  static {
    if (ExecutorsProvider.getJavaVersion() >= 21) {
      ExecutorsProvider.supplierExecutorService = ExecutorsProvider::getVirtualExecutorService;
      ExecutorsProvider.LOGGER.info("Java version is {}, using virtual thread executor", ExecutorsProvider.getJavaVersion());
    } else {
      ExecutorsProvider.supplierExecutorService = ExecutorsProvider::getDefaultExecutorService;
      ExecutorsProvider.LOGGER.info("Java version is {}, using default thread executor", ExecutorsProvider.getJavaVersion());
    }
  }
  
  /**
   * Returns an {@link ExecutorService} appropriate for the current Java version.
   *
   * @return a virtual thread executor (Java 21+) or a single-thread executor
   */
  public static ExecutorService getExecutorService() {
    return ExecutorsProvider.supplierExecutorService.get();
  }
  
  /**
   * Creates a single-thread executor for Java versions below 21.
   *
   * @return a single-thread executor
   */
  @SneakyThrows
  private static ExecutorService getDefaultExecutorService() {
    return Executors.newSingleThreadExecutor();
  }
  
  /**
   * Creates a virtual thread executor using reflection (Java 21+).
   *
   * @return a virtual thread per task executor
   */
  @SneakyThrows
  private static ExecutorService getVirtualExecutorService() {
    final Class<?> clazzThread = Executors.class;
    final Method ofVirtualMethod = clazzThread.getMethod("newVirtualThreadPerTaskExecutor");
    return ExecutorService.class.cast(ofVirtualMethod.invoke(null));
  }

  /**
   * Parses the Java runtime version.
   *
   * @return the major Java version number
   */
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
