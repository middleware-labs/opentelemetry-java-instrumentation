/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.config;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class ConfigManager {
  private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());

  public Map<String, String> getProperties() {
    Map<String, String> properties = new HashMap<>();

    configureEndpoint(properties);
    configurePropagators(properties);
    configureExporters(properties);
    configureCompression(properties);
    configureLogLevel(properties);
    configureAdditionalProperties(properties);

    return properties;
  }

  private void configureEndpoint(Map<String, String> properties) {
    String envConfigTarget =
        EnvironmentConfig.getEnvConfigValue("OTEL_EXPORTER_OTLP_ENDPOINT", "MW_TARGET");
    String mwAgentService = EnvironmentConfig.getMwAgentService();


    String endpoint="";

    if (envConfigTarget != null && !envConfigTarget.isEmpty()) {
      endpoint = envConfigTarget;
    } else if (mwAgentService != null
        && !mwAgentService.isEmpty()
        && !mwAgentService.equals("localhost")) {
      endpoint = "http://" + mwAgentService + ":9319";

      endpoint = EnvironmentConfig.get(EnvironmentConfig.EnvVar.MW_TARGET);
      if (endpoint == null || endpoint.isEmpty()) {
        endpoint = "http://localhost:9319";
      }
    }

    properties.put("otel.exporter.otlp.endpoint", endpoint);
  }

  private void configurePropagators(Map<String, String> properties) {
    String envConfigPropagators =
        EnvironmentConfig.getEnvConfigValue("OTEL_PROPAGATORS", "MW_PROPAGATORS");

    if (envConfigPropagators == null || envConfigPropagators.isEmpty()) {
      envConfigPropagators = EnvironmentConfig.get(EnvironmentConfig.EnvVar.MW_PROPAGATORS);
    }

    properties.put("otel.propagators", envConfigPropagators);
  }

  private void configureExporters(Map<String, String> properties) {
    properties.put("otel.metrics.exporter", "otlp");
    properties.put("otel.logs.exporter", "otlp");
    properties.put("otel.exporter.otlp.protocol", "grpc");
  }

  private void configureCompression(Map<String, String> properties) {
    boolean enableGzip = EnvironmentConfig.isMwGzipEnabled();
    if (enableGzip) {
      LOGGER.info("GZIP compression is enabled");
      properties.put("otel.exporter.otlp.compression", "gzip");
    }
  }

  private void configureLogLevel(Map<String, String> properties) {
    String logLevel = EnvironmentConfig.getEnvConfigValue("OTEL_LOG_LEVEL", "MW_LOG_LEVEL");
    if (logLevel == null || logLevel.isEmpty()) {
      logLevel = EnvironmentConfig.get(EnvironmentConfig.EnvVar.MW_LOG_LEVEL);
    }
    if (logLevel != null && !logLevel.isEmpty()) {
      properties.put("otel.log.level", logLevel);
    }
  }

  private void configureAdditionalProperties(Map<String, String> properties) {
    properties.put("otel.instrumentation.runtime-telemetry-java17.enable-all", "true");
    // Add any other additional properties here
  }

  private String getLogLevel() {
    String otelLogLevel = System.getenv("OTEL_LOG_LEVEL");
    String mwLogLevel = EnvironmentConfig.getMwLogLevel();

    if (otelLogLevel != null && !otelLogLevel.isEmpty()) {
      return otelLogLevel;
    } else if (mwLogLevel != null && !mwLogLevel.isEmpty()) {
      return mwLogLevel;
    }

    return null;
  }
}
