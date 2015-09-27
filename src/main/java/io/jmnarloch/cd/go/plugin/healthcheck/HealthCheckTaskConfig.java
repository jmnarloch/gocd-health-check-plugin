/**
 * Copyright (c) 2015 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.jmnarloch.cd.go.plugin.healthcheck;

import io.jmnarloch.cd.go.plugin.api.config.ConfigProperty;
import io.jmnarloch.cd.go.plugin.api.config.PropertyName;

/**
 * Enumerates the task configuration.
 *
 * @author Jakub Narloch
 */
public enum HealthCheckTaskConfig {

    @ConfigProperty(defaultValue = "http://localhost:8080/health", required = true)
    URL("Url"),

    @ConfigProperty(defaultValue = "status", required = true)
    ATTRIBUTE("Attribute"),

    @ConfigProperty(defaultValue = "UP", required = true)
    STATUS("Status"),

    @ConfigProperty(defaultValue = "15", required = true)
    DELAY("Delay"),

    @ConfigProperty(defaultValue = "60", required = true)
    TIMEOUT("Timeout");

    @PropertyName
    private final String name;

    HealthCheckTaskConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
