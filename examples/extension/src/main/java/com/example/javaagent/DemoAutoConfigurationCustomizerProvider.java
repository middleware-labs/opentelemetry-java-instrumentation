/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import com.example.healthcheck.HealthCheck;
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

    // Add commit-sha as Resource Attribute for OpsAI
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

    // Add repository url as a Resource Attribute for OpsAI
    String repoUrl = EnvironmentConfig.getMwVcsRepositoryUrl();
    if (repoUrl == null || repoUrl.isEmpty()) {
      LOGGER.info("MW_VCS_REPOSITORY_URL not set, attempting Git auto-detection");
      repoUrl = VcsUtils.getRepositoryUrl();
    }
    if (repoUrl != null && !repoUrl.isEmpty()) {
      builder.put("vcs.repository_url", repoUrl);
      LOGGER.info("Added vcs.repository_url: " + repoUrl);
    }

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
    return loggerProvider
        .setResource(createCommonResource())
        .addLogRecordProcessor(DemoLogRecordProcessor.getInstance());
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
    return tracerProvider.setResource(createCommonResource());
  }

  private Map<String, String> getDefaultProperties() {
    ConfigManager configManager = new ConfigManager();
    LOGGER.info(configManager.getProperties().toString());
    return configManager.getProperties();
  }
}
