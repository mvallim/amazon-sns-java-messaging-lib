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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExecutorsProvider {
  
  private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorsProvider.class);
  
  private static Supplier<ExecutorService> supplierExecutorService;
  
  static {
    if (ExecutorsProvider.getJavaVersion() >= 21) {
      ExecutorsProvider.supplierExecutorService = ExecutorsProvider::getVirtualThreadExecutor;
      ExecutorsProvider.LOGGER.info("Java version is {}, using virtual thread executor", ExecutorsProvider.getJavaVersion());
    } else {
      ExecutorsProvider.supplierExecutorService = ExecutorsProvider::getDefaultThreadExecutor;
      ExecutorsProvider.LOGGER.info("Java version is {}, using default thread executor", ExecutorsProvider.getJavaVersion());
    }
  }

  public static ExecutorService getThreadExecutor() {
    return ExecutorsProvider.supplierExecutorService.get();
  }

  @SneakyThrows
  private static ExecutorService getDefaultThreadExecutor() {
    return Executors.newSingleThreadExecutor();
  }

  @SneakyThrows
  private static ExecutorService getVirtualThreadExecutor() {
    final Class<?> clazzThread = Executors.class;
    final Method ofVirtualMethod = clazzThread.getMethod("newVirtualThreadPerTaskExecutor");
    return ExecutorService.class.cast(ofVirtualMethod.invoke(null));
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
