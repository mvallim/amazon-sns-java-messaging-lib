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
