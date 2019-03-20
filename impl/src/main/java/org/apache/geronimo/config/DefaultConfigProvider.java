/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.geronimo.config;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.inject.Typed;
import javax.enterprise.inject.Vetoed;

import javax.config.Config;
import javax.config.spi.ConfigBuilder;
import javax.config.spi.ConfigProviderResolver;


/**
 * @author <a href="mailto:struberg@apache.org">Mark Struberg</a>
 */
@Typed
@Vetoed
public class DefaultConfigProvider extends ConfigProviderResolver {

    private static Map<ClassLoader, Config> configs = new ConcurrentHashMap<>();


    @Override
    public Config getConfig() {
        return getConfig(Thread.currentThread().getContextClassLoader());
    }


    @Override
    public Config getConfig(ClassLoader forClassLoader) {

        Config config = existingConfig(forClassLoader);
        if (config == null) {
            synchronized (DefaultConfigProvider.class) {
                config = existingConfig(forClassLoader);
                if (config == null) {
                    config = getBuilder().forClassLoader(forClassLoader)
                            .addDefaultSources()
                            .addDiscoveredSources()
                            .addDiscoveredConverters()
                            .build();
                    registerConfig(config, forClassLoader);
                }
            }
        }
        return config;
    }

    Config existingConfig(ClassLoader forClassLoader) {
        return configs.get(forClassLoader);
    }


    @Override
    public void registerConfig(Config config, ClassLoader forClassLoader) {
        synchronized (DefaultConfigProvider.class) {
            configs.put(forClassLoader, config);
        }
    }

    @Override
    public ConfigBuilder getBuilder() {
        return new DefaultConfigBuilder();
    }


    @Override
    public void releaseConfig(Config config) {
        if (config == null) {
            // get the config from the current TCCL
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = DefaultConfigProvider.class.getClassLoader();
            }
            config = existingConfig(classLoader);
        }

        if (config != null) {
            synchronized (DefaultConfigProvider.class) {
                Iterator<Map.Entry<ClassLoader, Config>> it = configs.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<ClassLoader, Config> entry = it.next();
                    if (entry.getValue() != null && entry.getValue() == config) {
                        it.remove();
                        break;
                    }
                }
            }

            if (config instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) config).close();
                }
                catch (Exception e) {
                    throw new RuntimeException("Error while closing Config", e);
                }
            }

        }
    }
}
