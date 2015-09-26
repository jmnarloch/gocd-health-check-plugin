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

import com.fasterxml.jackson.databind.JsonNode;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionConfiguration;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionContext;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionResult;
import io.jmnarloch.cd.go.plugin.api.executor.TaskExecutor;
import org.reactivestreams.Publisher;
import reactor.Environment;
import reactor.fn.Function;
import reactor.fn.Predicate;
import reactor.io.buffer.Buffer;
import reactor.io.codec.json.JsonCodec;
import reactor.io.net.NetStreams;
import reactor.io.net.http.HttpChannel;
import reactor.rx.Promise;
import reactor.rx.Streams;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class HealthCheckTaskExecutor implements TaskExecutor {

    private final Logger logger = Logger.getLoggerFor(HealthCheckTaskExecutor.class);

    @Override
    public ExecutionResult execute(ExecutionContext context, ExecutionConfiguration config, JobConsoleLogger console) {

        final JsonCodec<JsonNode, JsonNode> codec = new JsonCodec<>(JsonNode.class);

        try {
            Environment.initialize();

            final String healthCheckUrl = getProperty(config, HealthCheckTaskConfig.URL);
            final String attribute = getProperty(config, HealthCheckTaskConfig.ATTRIBUTE);
            final String status = getProperty(config, HealthCheckTaskConfig.STATUS);
            final int timeout = getIntProperty(config, HealthCheckTaskConfig.TIMEOUT, 60);
            final int retryDelay = getIntProperty(config, HealthCheckTaskConfig.DELAY, 15);

            final boolean success = Streams.period(retryDelay, TimeUnit.SECONDS)
                    .flatMap(healthCheck(healthCheckUrl, codec))
                    .map(mapStatus(attribute))
                    .filter(expectStatus(status))
                    .next()
                    .awaitSuccess(timeout, TimeUnit.SECONDS);

            if(!success) {
                return ExecutionResult.failure("Health check failed");
            }

            return ExecutionResult.success("Health check succeeded");
        } catch (Exception e) {

            logger.error("Unexpected error occurred when executing task", e);
            return ExecutionResult.failure("Health check failed", e);
        }
    }

    private Function<Long, Publisher<JsonNode>> healthCheck(final String healthCheckUrl, final JsonCodec<JsonNode, JsonNode> codec) {
        return new Function<Long, Publisher<JsonNode>>() {
            public Publisher<JsonNode> apply(Long aLong) {
                return healthCheck(healthCheckUrl)
                        .flatMap(decodeInstanceStatus(codec));
            }
        };
    }

    private Promise<? extends HttpChannel<Buffer, Buffer>> healthCheck(final String healthCheckUrl) {
        return NetStreams.httpClient().get(healthCheckUrl);
    }

    private <T> Function<HttpChannel<Buffer, Buffer>, Publisher<T>> decodeInstanceStatus(
            final JsonCodec<T, T> codec) {
        return new Function<HttpChannel<Buffer, Buffer>, Publisher<T>>() {
            public Publisher<T> apply(HttpChannel<Buffer, Buffer> bufferBufferHttpChannel) {
                return bufferBufferHttpChannel.decode(codec);
            }
        };
    }

    private Function<JsonNode, String> mapStatus(final String attribute) {
        return new Function<JsonNode, String>() {
            public String apply(JsonNode instanceStatus) {
                return instanceStatus.get(attribute).textValue();
            }
        };
    }

    private Predicate<String> expectStatus(final String status) {
        return new Predicate<String>() {
            public boolean test(String instanceStatus) {
                return status.equals(instanceStatus);
            }
        };
    }

    private int getIntProperty(ExecutionConfiguration config, HealthCheckTaskConfig property, int defaultValue) {
        final String value = getProperty(config, property);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private String getProperty(ExecutionConfiguration config, HealthCheckTaskConfig property) {
        return config.getProperty(property.getName());
    }
}
