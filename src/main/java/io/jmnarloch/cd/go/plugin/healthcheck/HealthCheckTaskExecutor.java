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

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionConfiguration;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionContext;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionResult;
import io.jmnarloch.cd.go.plugin.api.executor.TaskExecutor;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;
import rx.functions.Func1;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * The health check executor. The plugin will perform polling of the configured health url until it will receive
 * the first successful response matching the specific instance status. If the connection can not be establish or
 * the health check statuses will not match the expected value the pooling will be timeouted after configured number
 * of seconds and the step itself will result in build error.
 *
 * @author Jakub Narloch
 */
public class HealthCheckTaskExecutor implements TaskExecutor {

    /**
     * The dot notation separator.
     */
    private static final String SEPARATOR = "\\.";

    /**
     * The logger used by this class.
     */
    private final Logger logger = Logger.getLoggerFor(HealthCheckTaskExecutor.class);

    /**
     * The JSON parser.
     */
    private final JsonParser parser;

    /**
     * Creates new instance of {@link HealthCheckTaskExecutor}.
     */
    public HealthCheckTaskExecutor() {
        this(new JsonParser());
    }

    /**
     * Creates new instance of {@link HealthCheckTaskExecutor} with specific JSON parser.
     *
     * @param parser the JSON parser
     */
    public HealthCheckTaskExecutor(JsonParser parser) {
        this.parser = parser;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExecutionResult execute(ExecutionContext context, ExecutionConfiguration config, JobConsoleLogger console) {

        try {
            final String healthCheckUrl = getProperty(config, HealthCheckTaskConfig.URL);
            final String attribute = getProperty(config, HealthCheckTaskConfig.ATTRIBUTE);
            final String status = getProperty(config, HealthCheckTaskConfig.STATUS);
            final int timeout = getIntProperty(config, HealthCheckTaskConfig.TIMEOUT, 60);
            final int retryDelay = getIntProperty(config, HealthCheckTaskConfig.DELAY, 15);

            final boolean success = RxNetty.createHttpGet(healthCheckUrl)
                    .flatMap(parseJsonElement())
                    .map(mapStatusAttribute(attribute))
                    .map(mapAttributeValue())
                    .map(matchStatus(status))
                    .filter(filterStatuses())
                    .switchIfEmpty(Observable.<Boolean>error(null))
                    .retryWhen(retryPolicy(retryDelay, timeout))
                    .timeout(timeout, TimeUnit.SECONDS)
                    .toBlocking()
                    .firstOrDefault(false);

            if (!success) {
                return ExecutionResult.failure("Health check failed");
            }

            return ExecutionResult.success("Health check succeeded");
        } catch (Exception e) {

            logger.error("Unexpected error occurred when executing task", e);
            return ExecutionResult.failure("Health check failed", e);
        }
    }

    /**
     * Maps the HTTP response and unmarshalls it's JSON payload.
     *
     * @return the mapping function
     */
    private Func1<HttpClientResponse<ByteBuf>, Observable<JsonElement>> parseJsonElement() {
        return new Func1<HttpClientResponse<ByteBuf>, Observable<JsonElement>>() {
            @Override
            public Observable<JsonElement> call(HttpClientResponse<ByteBuf> response) {
                return response.getContent().map(new Func1<ByteBuf, JsonElement>() {
                    @Override
                    public JsonElement call(ByteBuf byteBuf) {
                        return parser.parse(byteBuf.toString(StandardCharsets.UTF_8));
                    }
                });
            }
        };
    }

    /**
     * Maps the attribute that indicates the instance status.
     *
     * @param attribute the attribute name
     * @return the mapping function
     */
    private Func1<JsonElement, JsonElement> mapStatusAttribute(final String attribute) {
        return new Func1<JsonElement, JsonElement>() {
            @Override
            public JsonElement call(JsonElement jsonElement) {
                JsonElement element = jsonElement;
                final String[] parts = attribute.split(SEPARATOR);
                for(String part : parts) {
                    element = element.getAsJsonObject().get(part);
                }
                return element;
            }
        };
    }

    /**
     * Maps the attribute string value.
     *
     * @return the mapping function
     */
    private Func1<JsonElement, String> mapAttributeValue() {
        return new Func1<JsonElement, String>() {
            @Override
            public String call(JsonElement jsonElement) {
                return jsonElement.getAsString();
            }
        };
    }

    /**
     * Matches the expected application status.
     *
     * @param status the status
     * @return the mapping function
     */
    private Func1<String, Boolean> matchStatus(final String status) {
        return new Func1<String, Boolean>() {
            @Override
            public Boolean call(String instanceStatus) {
                return StringUtils.equalsIgnoreCase(status, instanceStatus);
            }
        };
    }

    /**
     * Filters the application status.
     *
     * @return the predicate
     */
    private Func1<Boolean, Boolean> filterStatuses() {
        return new Func1<Boolean, Boolean>() {
            @Override
            public Boolean call(Boolean value) {
                return Boolean.TRUE.equals(value);
            }
        };
    }

    /**
     * Specifies the retry policy for the {@link Observable}.
     *
     * @param retryDelay the delay
     * @param timeout the maximum timeout
     * @return the retry policy
     */
    private Func1<Observable<? extends Throwable>, Observable<?>> retryPolicy(final int retryDelay, final int timeout) {
        return new Func1<Observable<? extends Throwable>, Observable<?>>() {
            @Override
            public Observable<?> call(Observable<? extends Throwable> observable) {
                final int retries = retryDelay > 0 && retryDelay <= timeout ? timeout / retryDelay : 0;

                return Observable.interval(retryDelay, TimeUnit.SECONDS).take(retries);
            }
        };
    }

    /**
     * Retrieves the integer property value.
     *
     * @param config the configuration
     * @param property the property name
     * @param defaultValue the default value
     * @return the property value
     */
    private int getIntProperty(ExecutionConfiguration config, HealthCheckTaskConfig property, int defaultValue) {
        final String value = getProperty(config, property);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    /**
     * Retrieves the property value.
     *
     * @param config the configuration
     * @param property the property name
     * @return the property value
     */
    private String getProperty(ExecutionConfiguration config, HealthCheckTaskConfig property) {
        return config.getProperty(property.getName());
    }
}
