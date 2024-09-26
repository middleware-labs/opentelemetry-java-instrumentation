/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.config;

import java.util.Arrays;

/** Environmental configurations. */
public class EnvironmentConfig {

  public static final String MW_PROFILING_SERVER_URL =
      System.getenv().get("MW_PROFILING_SERVER_URL");

  public static final String MW_PROFILING_ALLOC =
      System.getenv().getOrDefault("MW_PROFILING_ALLOC", "512k");

  public static final String MW_PROFILING_LOCK =
      System.getenv().getOrDefault("MW_PROFILING_LOCK", "10ms");

  public static final String MW_AGENT_SERVICE =
      System.getenv().getOrDefault("MW_AGENT_SERVICE", "localhost");

  public static final String MW_AUTH_URL =
      System.getenv().getOrDefault("MW_AUTH_URL", "https://app.middleware.io/api/v1/auth");

  public static final boolean MW_APM_COLLECT_PROFILING =
      Arrays.asList("true", "1", "t", "y", "yes")
          .contains(System.getenv().getOrDefault("MW_APM_COLLECT_PROFILING", "True").toLowerCase());

  public static final boolean MW_APM_COLLECT_TRACES =
      Arrays.asList("true", "1", "t", "y", "yes")
          .contains(System.getenv().getOrDefault("MW_APM_COLLECT_TRACES", "True").toLowerCase());

  public static final boolean MW_APM_COLLECT_LOGS =
      Arrays.asList("true", "1", "t", "y", "yes")
          .contains(System.getenv().getOrDefault("MW_APM_COLLECT_LOGS", "True").toLowerCase());

  public static final boolean MW_APM_COLLECT_METRICS =
      Arrays.asList("true", "1", "t", "y", "yes")
          .contains(System.getenv().getOrDefault("MW_APM_COLLECT_METRICS", "True").toLowerCase());

  public static final boolean MW_DISABLE_TELEMETRY =
      Arrays.asList("true", "1", "t", "y", "yes")
          .contains(System.getenv().getOrDefault("MW_DISABLE_TELEMETRY", "True").toLowerCase());

  public static final String MW_TARGET = System.getenv().getOrDefault("MW_TARGET", "");

  public static final String MW_API_KEY = System.getenv().getOrDefault("MW_API_KEY", null);

  public static final String MW_PROPAGATORS = System.getenv().getOrDefault("MW_PROPOGATORS", "b3");

  public static final String MW_CUSTOM_RESOURCE_ATTRIBUTE =
      System.getenv("MW_CUSTOM_RESOURCE_ATTRIBUTE");

  public static final String MW_LOG_LEVEL = System.getenv().getOrDefault("MW_LOG_LEVEL", null);

  public static String getEnvConfigValue(String otelKey, String mwKey) {
    // THe otel env has higher priority
    String otelValue = System.getenv(otelKey);
    if (otelValue != null && !otelValue.isEmpty()) {
      return otelValue;
    }

    String mwValue = System.getenv(mwKey);
    if (mwValue != null && !mwValue.isEmpty()) {
      return mwValue;
    }
    return null;
  }
}
