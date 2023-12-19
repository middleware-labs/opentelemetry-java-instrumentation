/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.config;

/** Environmental configurations. */
public class EnvironmentConfig {

  public static final String MW_PROFILING_SERVER_URL =
      System.getenv().getOrDefault("MW_PROFILING_SERVER_URL", "https://profiling.middleware.io");

  public static final String MW_PROFILING_ALLOC =
      System.getenv().getOrDefault("MW_PROFILING_ALLOC", "512k");

  public static final String MW_PROFILING_LOCK =
      System.getenv().getOrDefault("MW_PROFILING_LOCK", "10ms");

  public static final String MW_AGENT_SERVICE =
      System.getenv().getOrDefault("MW_AGENT_SERVICE", "localhost");

  public static final String MW_AUTH_URL =
      System.getenv().getOrDefault("MW_AUTH_URL", "https://app.middleware.io/api/v1/auth");

  public static final String MW_API_KEY = System.getenv().getOrDefault("MW_API_KEY", null);
}
