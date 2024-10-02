/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.example.healthcheck.HealthCheck;
import com.example.javaagent.config.ConfigManager;
import com.example.javaagent.config.EnvironmentConfig;
import com.example.profile.PyroscopeProfile;
import com.google.auto.service.AutoService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

@AutoService(AutoConfigurationCustomizerProvider.class)
public class DemoAutoConfigurationCustomizerProvider
    implements AutoConfigurationCustomizerProvider {

  private static final Logger LOGGER =
      Logger.getLogger(DemoAutoConfigurationCustomizerProvider.class.getName());

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    HealthCheck check = new HealthCheck();
    boolean isHealthy = check.isHealthy();
    check.logHealthCheckResult(isHealthy);
    PyroscopeProfile.startProfiling();
    autoConfiguration
        .addLoggerProviderCustomizer(this::configureSdkLoggerProvider)
        .addMeterProviderCustomizer(this::configureSdkMeterProvider)
        .addTracerProviderCustomizer(this::configureSdkTraceProvider)
        .addPropertiesSupplier(this::getDefaultProperties);
  }

  private SdkMeterProviderBuilder configureSdkMeterProvider(
      SdkMeterProviderBuilder meterProvider, ConfigProperties config) {
    try {
      if (!EnvironmentConfig.isMwApmCollectMetrics()) {
        return meterProvider.setResource(Resource.empty());
      }
      Field resourceField = meterProvider.getClass().getDeclaredField("resource");
      resourceField.setAccessible(true);
      Resource resource = (Resource) resourceField.get(meterProvider);

      // Parse OTEL_RESOURCE_ATTRIBUTES
      Attributes otelAttributes =
          parseResourceAttributes(System.getenv("OTEL_RESOURCE_ATTRIBUTES"));

      // Parse MW_CUSTOM_RESOURCE_ATTRIBUTE
      Attributes mwAttributes =
          parseResourceAttributes(EnvironmentConfig.getMwCustomResourceAttribute());

      // Merge attributes, giving priority to OTEL_RESOURCE_ATTRIBUTES
      AttributesBuilder mergedBuilder =
          Attributes.builder().putAll(mwAttributes).putAll(otelAttributes);
      mergedBuilder.put("runtime.metrics.java", "true");
      mergedBuilder.put("mw.app.lang", "java");
      Attributes mergedAttributes = mergedBuilder.build();

      meterProvider.setResource(resource.merge(Resource.create(mergedAttributes)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return meterProvider;
  }

  private Attributes parseResourceAttributes(String attributesString) {
    AttributesBuilder builder = Attributes.builder();
    if (attributesString != null && !attributesString.isEmpty()) {
      String[] pairs = attributesString.split(",");
      for (String pair : pairs) {
        String[] keyValue = pair.split("=");
        if (keyValue.length == 2) {
          builder.put(AttributeKey.stringKey(keyValue[0].trim()), keyValue[1].trim());
        }
      }
    }
    return builder.build();
  }

  private SdkLoggerProviderBuilder configureSdkLoggerProvider(
      SdkLoggerProviderBuilder loggerProvider, ConfigProperties config) {

    if (!EnvironmentConfig.isMwApmCollectLogs()) {
      LOGGER.warning("Otel logging is disabled");
      // Return a builder that will create a LoggerProvider with no processors
      return SdkLoggerProvider.builder()
          .setResource(Resource.empty())
          .addLogRecordProcessor(
              SimpleLogRecordProcessor.create(
                  new LogRecordExporter() {
                    @Override
                    public CompletableResultCode export(Collection<LogRecordData> logs) {
                      return CompletableResultCode.ofSuccess();
                    }

                    @Override
                    public CompletableResultCode flush() {
                      return CompletableResultCode.ofSuccess();
                    }

                    @Override
                    public CompletableResultCode shutdown() {
                      return CompletableResultCode.ofSuccess();
                    }
                  }));
    }
    return loggerProvider.addLogRecordProcessor(DemoLogRecordProcessor.getInstance());
  }

  private SdkTracerProviderBuilder configureSdkTraceProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {
    if (!EnvironmentConfig.isMwApmCollectTraces()) {
      LOGGER.warning("Otel tracing is disabled");
      // Return a builder that will create a TracerProvider with no samplers and no span processors
      return SdkTracerProvider.builder()
          .setResource(Resource.empty())
          .setSampler(Sampler.alwaysOff());
    }
    return tracerProvider;
  }

  private Map<String, String> getDefaultProperties() {
    ConfigManager configManager = new ConfigManager();
    LOGGER.info(configManager.getProperties().toString());
    return configManager.getProperties();
  }
}
