/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.example.javaagent.config.EnvironmentConfig;
import com.example.profile.PyroscopeProfile;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.resources.Resource;
import java.lang.reflect.Field;
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
 */
@AutoService(AutoConfigurationCustomizerProvider.class)
public class DemoAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    PyroscopeProfile.startProfiling();
    autoConfiguration
        .addLoggerProviderCustomizer(this::configureSdkLoggerProvider)
        .addMeterProviderCustomizer(this::configureSdkMeterProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private SdkMeterProviderBuilder configureSdkMeterProvider(
      SdkMeterProviderBuilder meterProvider, ConfigProperties config) {
    try {
      Field resourceField = meterProvider.getClass().getDeclaredField("resource");
      resourceField.setAccessible(true);
      Resource resource = (Resource) resourceField.get(meterProvider);
      meterProvider.setResource(
          resource.merge(
              Resource.create(
                  Attributes.of(
                      AttributeKey.stringKey("runtime.metrics.java"), "true",
                      AttributeKey.stringKey("mw.app.lang"), "java"))));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return meterProvider;
  }

  private SdkLoggerProviderBuilder configureSdkLoggerProvider(
      SdkLoggerProviderBuilder loggerProvider, ConfigProperties config) {

    return loggerProvider.addLogRecordProcessor(DemoLogRecordProcessor.getInstance());
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put(
        "otel.exporter.otlp.endpoint", "http://" + EnvironmentConfig.MW_AGENT_SERVICE + ":9319");
    properties.put("otel.metrics.exporter", "otlp");
    properties.put("otel.logs.exporter", "otlp");
    properties.put("otel.exporter.otlp.protocol", "grpc");
    properties.put("otel.instrumentation.runtime-telemetry-java17.enable-all", "true");
    return properties;
  }
}
