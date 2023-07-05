/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.google.auto.service.AutoService;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import java.util.HashMap;
import java.util.Map;

/**
 * This is one of the main entry points for Instrumentation Agent's customizations. It allows
 * configuring the {@link AutoConfigurationCustomizer}. See the {@link
 * #customize(AutoConfigurationCustomizer)} method below.
 *
 * <p>Also see https://github.com/open-telemetry/opentelemetry-java/issues/2022
 *
 * @see AutoConfigurationCustomizerProvider
 * @see DemoPropagatorProvider
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class DemoAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    autoConfiguration
        .addLoggerProviderCustomizer(this::configureSdkLoggerProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private SdkLoggerProviderBuilder configureSdkLoggerProvider(
      SdkLoggerProviderBuilder loggerProvider, ConfigProperties config) {

    return loggerProvider.addLogRecordProcessor(new DemoLogRecordProcessor());
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put("otel.exporter.otlp.endpoint", "http://localhost:9319");
    properties.put("otel.metrics.exporter", "otlp");
    properties.put("otel.logs.exporter", "otlp");
    properties.put("otel.instrumentation.runtime-telemetry-java17.enable-all", "true");
    return properties;
  }
}
