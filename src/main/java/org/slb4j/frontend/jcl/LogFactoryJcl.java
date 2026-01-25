/*
 * Copyright 2026 Axel Howind - axh@dua3.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slb4j.frontend.jcl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogConfigurationException;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.slb4j.SLB4J;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * JCL SPI implementation for SLB4J.
 */
public final class LogFactoryJcl extends LogFactory {

    static {
        SLB4J.init();
    }

    private final ConcurrentMap<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * Constructor.
     */
    public LogFactoryJcl() {
        // nothing to do
    }

    @Override
    public @Nullable Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public String[] getAttributeNames() {
        return attributes.keySet().toArray(new String[0]);
    }

    @Override
    public Log getInstance(Class<?> clazz) throws LogConfigurationException {
        return getInstance(clazz.getName());
    }

    @Override
    public Log getInstance(String name) throws LogConfigurationException {
        return new LoggerJcl(name);
    }

    @Override
    public void release() {
        attributes.clear();
    }

    @Override
    public void removeAttribute(String name) {
        attributes.remove(name);
    }

    @Override
    public void setAttribute(String name, @Nullable Object value) {
        if (value == null) {
            attributes.remove(name);
        } else {
            attributes.put(name, value);
        }
    }
}
