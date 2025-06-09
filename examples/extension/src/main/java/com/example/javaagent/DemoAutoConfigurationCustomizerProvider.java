/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.example.healthcheck.HealthCheck;
import com.example.javaagent.codeextraction.EnhancedExceptionSpanExporter;
import com.example.javaagent.config.ConfigManager;
import com.example.javaagent.config.EnvironmentConfig;
import com.example.javaagent.vcsintegration.VcsUtils;
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
import io.opentelemetry.sdk.trace.export.SpanExporter;
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

    // Store the original exporter configuration
    autoConfiguration.addPropertiesSupplier(this::getDefaultProperties);

    autoConfiguration
        .addLoggerProviderCustomizer(this::configureSdkLoggerProvider)
        .addMeterProviderCustomizer(this::configureSdkMeterProvider)
        .addTracerProviderCustomizer(this::configureSdkTraceProvider)
        // Add a customizer for span exporters
        .addSpanExporterCustomizer(this::customizeSpanExporter);
  }

  /** Customize the span exporter to wrap it with our enhanced exception exporter */
  private SpanExporter customizeSpanExporter(SpanExporter spanExporter, ConfigProperties config) {
    if (!EnvironmentConfig.isMwApmCollectTraces()) {
      return spanExporter;
    }

    LOGGER.info("🔧 Wrapping default span exporter with EnhancedExceptionSpanExporter");

    // Wrap the default exporter with our enhanced exporter
    return new EnhancedExceptionSpanExporter(spanExporter);
  }

  private Resource createCommonResource() {
    AttributesBuilder builder =
        Attributes.builder()
            .put("runtime.metrics.java", "true")
            .put("mw.app.lang", "java")
            .put("mw.account_key", EnvironmentConfig.getMwApiKey())
            .put("service.name", EnvironmentConfig.getMwServiceName());

    // Parse OTEL_RESOURCE_ATTRIBUTES
    Attributes otelAttributes = parseResourceAttributes(System.getenv("OTEL_RESOURCE_ATTRIBUTES"));
    builder.putAll(otelAttributes);

    // Parse MW_CUSTOM_RESOURCE_ATTRIBUTE
    Attributes mwAttributes =
        parseResourceAttributes(EnvironmentConfig.getMwCustomResourceAttribute());
    builder.putAll(mwAttributes);

    // VCS Integration - Environment variables take precedence, then auto-detection
    String commitShaValue = EnvironmentConfig.getMwVcsCommitSha();
    if (commitShaValue == null || commitShaValue.isEmpty()) {
      LOGGER.info("MW_VCS_COMMIT_SHA not set, attempting Git auto-detection");
      commitShaValue = VcsUtils.getCurrentCommitSha();
    }
    if (commitShaValue != null && !commitShaValue.isEmpty()) {
      builder.put("vcs.commit_sha", commitShaValue);
      LOGGER.info("Added vcs.commit_sha: " + commitShaValue);
    }

    String repoUrl = EnvironmentConfig.getMwVcsRepositoryUrl();
    if (repoUrl == null || repoUrl.isEmpty()) {
      LOGGER.info("MW_VCS_REPOSITORY_URL not set, attempting Git auto-detection");
      repoUrl = VcsUtils.getRepositoryUrl();
    }
    if (repoUrl != null && !repoUrl.isEmpty()) {
      builder.put("vcs.repository_url", repoUrl);
      LOGGER.info("Added vcs.repository_url: " + repoUrl);
    }

    builder.put("telemetry.sdk.language", "java");

    return Resource.create(builder.build());
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

      meterProvider.setResource(resource.merge(createCommonResource()));
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
    return loggerProvider
        .setResource(createCommonResource())
        .addLogRecordProcessor(DemoLogRecordProcessor.getInstance());
  }

  private SdkTracerProviderBuilder configureSdkTraceProvider(
      SdkTracerProviderBuilder tracerProvider, ConfigProperties config) {
    if (!EnvironmentConfig.isMwApmCollectTraces()) {
      LOGGER.warning("Otel tracing is disabled");
      return SdkTracerProvider.builder()
          .setResource(Resource.empty())
          .setSampler(Sampler.alwaysOff());
    }

    // Only set the resource here, don't add any span processors
    // The span exporter will be customized via addSpanExporterCustomizer
    return tracerProvider.setResource(createCommonResource());
  }

  private Map<String, String> getDefaultProperties() {
    ConfigManager configManager = new ConfigManager();
    Map<String, String> properties = configManager.getProperties();

    // Override to use debug exporter for now (since that's what your config shows)
    // Remove this when you want to use the actual OTLP exporter
    properties.put("otel.traces.exporter", "otlp");

    LOGGER.info(properties.toString());
    return properties;
  }
}
