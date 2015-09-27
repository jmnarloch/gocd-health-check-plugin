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

import io.jmnarloch.cd.go.plugin.api.validation.AbstractTaskValidator;
import io.jmnarloch.cd.go.plugin.api.validation.ValidationErrors;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * Validates the task configuration.
 *
 * @author Jakub Narloch
 */
public class HealthCheckTaskValidator extends AbstractTaskValidator {

    /**
     * {@inheritDoc}
     */
    @Override
    public void validate(Map<String, Object> properties, ValidationErrors errors) {

        rejectIfEmpty(properties, errors, HealthCheckTaskConfig.URL.getName(), "Url must be specified");
        rejectIfEmpty(properties, errors, HealthCheckTaskConfig.ATTRIBUTE.getName(), "Attribute must be specified");
        rejectIfEmpty(properties, errors, HealthCheckTaskConfig.STATUS.getName(), "Status must be specified");
    }

    /**
     * Rejects the value if it's empty.
     *
     * @param properties the properties
     * @param errors     the validation errors
     * @param name       the property name
     * @param message    the message
     */
    private void rejectIfEmpty(Map<String, Object> properties, ValidationErrors errors, String name, String message) {
        if(StringUtils.isBlank(getProperty(properties, name))) {
            errors.addError(name, message);
        }
    }
}
