package com.amazon.sns.messaging.lib.instrument;

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MBeanRegistrar {

  private static final MBeanServer BEAN_SERVER = ManagementFactory.getPlatformMBeanServer();

  @SneakyThrows
  public static void registerMBean(final Object mbean, final String name) {
    final ObjectName objectName = new ObjectName(name);

    if (!MBeanRegistrar.BEAN_SERVER.isRegistered(objectName)) {
      MBeanRegistrar.BEAN_SERVER.registerMBean(mbean, objectName);
    }
    
  }

  @SneakyThrows
  public static void unregisterMBean(final String name) {
    final ObjectName objectName = new ObjectName(name);
    
    if (MBeanRegistrar.BEAN_SERVER.isRegistered(objectName)) {
      MBeanRegistrar.BEAN_SERVER.unregisterMBean(objectName);
    }
    
  }
  
}
