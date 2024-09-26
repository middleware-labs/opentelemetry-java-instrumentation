/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.example.healthcheck.HealthCheck;
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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This is one of the main entry points for Instrumentation Agent's customizations. It allows
 * configuring the {@link AutoConfigurationCustomizer}. See the {@link
 * #customize(AutoConfigurationCustomizer)} method below.
 *
 * <p>Also see <a href="https://github.com/open-telemetry/opentelemetry-java/issues/2022">...</a>
 *
 * @see AutoConfigurationCustomizerProvider
 */
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
      if (!EnvironmentConfig.MW_APM_COLLECT_METRICS) {
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
          parseResourceAttributes(EnvironmentConfig.MW_CUSTOM_RESOURCE_ATTRIBUTE);

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

    if (!EnvironmentConfig.MW_APM_COLLECT_LOGS) {
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
    if (!EnvironmentConfig.MW_APM_COLLECT_TRACES) {
      LOGGER.warning("Otel tracing is disabled");
      // Return a builder that will create a TracerProvider with no samplers and no span processors
      return SdkTracerProvider.builder()
          .setResource(Resource.empty())
          .setSampler(Sampler.alwaysOff());
    }
    return tracerProvider;
  }

  private Map<String, String> getDefaultProperties() {
    Map<String, String> properties = new HashMap<>();

    String envConfigTarget =
        EnvironmentConfig.getEnvConfigValue("OTEL_EXPORTER_OTLP_ENDPOINT", "MW_TARGET");

    String envConfigPropogators =
        EnvironmentConfig.getEnvConfigValue("OTEL_PROPAGATORS", "MW_PROPOGATORS");

    if (EnvironmentConfig.MW_AGENT_SERVICE != null
        && !EnvironmentConfig.MW_AGENT_SERVICE.isEmpty()) {
      properties.put(
          "otel.exporter.otlp.endpoint", "http://" + EnvironmentConfig.MW_AGENT_SERVICE + ":9319");
    } else if (envConfigTarget != null && !envConfigTarget.isEmpty()) {
      properties.put("otel.exporter.otlp.endpoint", envConfigTarget);
    }

    properties.put("otel.propagators", envConfigPropogators);

    properties.put("otel.metrics.exporter", "otlp");
    properties.put("otel.logs.exporter", "otlp");
    properties.put("otel.exporter.otlp.protocol", "grpc");
    properties.put("otel.instrumentation.runtime-telemetry-java17.enable-all", "true");

    String logLevel = getLogLevel();
    if (logLevel != null) {
      properties.put("otel.log.level", logLevel);
    }

    return properties;
  }

  private String getLogLevel() {
    String otelLogLevel = System.getenv("OTEL_LOG_LEVEL");
    String mwLogLevel = EnvironmentConfig.MW_LOG_LEVEL;

    if (otelLogLevel != null && !otelLogLevel.isEmpty()) {
      return otelLogLevel;
    } else if (mwLogLevel != null && !mwLogLevel.isEmpty()) {
      return mwLogLevel;
    }

    return null;
  }
}
