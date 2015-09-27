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

import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionConfiguration;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionContext;
import io.jmnarloch.cd.go.plugin.api.executor.ExecutionResult;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 *
 * @author Jakub Narloch
 */
public class HealthCheckTaskExecutorTest {

    private static final int PORT = 18080;
    private HealthCheckTaskExecutor instance;

    private HttpServer<ByteBuf, ByteBuf> server;

    @Before
    public void setUp() throws Exception {

        instance = new HealthCheckTaskExecutor();

        server = RxNetty.createHttpServer(PORT, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
                if ("/health".equals(request.getPath())) {

                    return response.writeStringAndFlush("{status: UP}");
                }
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                return response.close();
            }
        }).start();
    }

    @After
    public void tearDown() throws Exception {

        server.shutdown();
    }

    @Test
    public void shouldSuccess() {

        // given
        final ExecutionContext context = new ExecutionContext(new HashMap());
        final ExecutionConfiguration configuration = configuration(url("/health"), "status", "UP", 15, 30);
        final JobConsoleLogger logger = mock(JobConsoleLogger.class);

        // when
        ExecutionResult result = instance.execute(context, configuration, logger);

        // then
        assertNotNull(result);
        assertTrue(result.isSuccess());
    }

    @Test
    public void shouldTimeoutStatus() {

        // given
        final ExecutionContext context = new ExecutionContext(new HashMap());
        final ExecutionConfiguration configuration = configuration(url("/health"), "status", "OK", 15, 30);
        final JobConsoleLogger logger = mock(JobConsoleLogger.class);

        // when
        ExecutionResult result = instance.execute(context, configuration, logger);

        // then
        assertNotNull(result);
        assertFalse(result.isSuccess());
    }

    @Test
    public void shouldTimeoutConnection() {

        // given
        final ExecutionContext context = new ExecutionContext(new HashMap());
        final ExecutionConfiguration configuration = configuration(url("/"), "status", "UP", 15, 30);
        final JobConsoleLogger logger = mock(JobConsoleLogger.class);

        // when
        ExecutionResult result = instance.execute(context, configuration, logger);

        // then
        assertNotNull(result);
        assertFalse(result.isSuccess());
    }

    private String url(final String path) {
        return String.format("http://localhost:%d%s", PORT, path);
    }

    private ExecutionConfiguration configuration(String url, String attribute, String status, int delay, int timeout) {

        final HashMap<String, Object> configuration = new HashMap<>();
        addProperty(configuration, "Url", url);
        addProperty(configuration, "Attribute", attribute);
        addProperty(configuration, "Status", status);
        addProperty(configuration, "Delay", String.valueOf(delay));
        addProperty(configuration, "Timeout", String.valueOf(timeout));
        return new ExecutionConfiguration(configuration);
    }

    private void addProperty(HashMap<String, Object> configuration, String name, String value) {
        configuration.put(name, Collections.singletonMap("value", value));
    }

}