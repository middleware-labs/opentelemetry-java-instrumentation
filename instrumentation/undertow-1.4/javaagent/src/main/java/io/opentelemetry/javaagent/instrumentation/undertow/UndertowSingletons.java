/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.bootstrap.undertow.UndertowActiveHandlers;
import io.undertow.server.HttpServerExchange;

public final class UndertowSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.undertow-1.4";

  private static final Instrumenter<HttpServerExchange, HttpServerExchange> INSTRUMENTER;

  static {
    UndertowHttpAttributesGetter httpAttributesGetter = new UndertowHttpAttributesGetter();

    InstrumenterBuilder<HttpServerExchange, HttpServerExchange> builder =
        Instrumenter.<HttpServerExchange, HttpServerExchange>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.builder(httpAttributesGetter)
                    .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpServerAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(AgentCommonConfig.get().getServerRequestHeaders())
                    .setCapturedResponseHeaders(AgentCommonConfig.get().getServerResponseHeaders())
                    .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addContextCustomizer(
                HttpServerRoute.builder(httpAttributesGetter)
                    .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addContextCustomizer(
                (context, request, attributes) -> {
                  // span is ended when counter reaches 0, we start from 2 which accounts for the
                  // handler that started the span and exchange completion listener
                  context = UndertowActiveHandlers.init(context, 2);

                  return new AppServerBridge.Builder()
                      .captureServletAttributes()
                      .recordException()
                      .init(context);
                })
            .addOperationMetrics(HttpServerMetrics.get());
    if (AgentCommonConfig.get().shouldEmitExperimentalHttpServerTelemetry()) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildServerInstrumenter(UndertowExchangeGetter.INSTANCE);
  }

  private static final UndertowHelper HELPER = new UndertowHelper(INSTRUMENTER);

  public static UndertowHelper helper() {
    return HELPER;
  }

  private UndertowSingletons() {}
}
