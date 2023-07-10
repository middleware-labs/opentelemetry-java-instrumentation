/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.config;

/** Environmental configurations. */
public class EnvironmentConfig {

  public static final String MW_PROFILING_SERVER_URL =
      System.getenv().getOrDefault("MW_PROFILING_SERVER_URL", "https://profiling.middleware.io");

  public static final String TARGET = System.getenv().getOrDefault("TARGET", null);

  public static final String MW_AGENT_SERVICE =
      System.getenv().getOrDefault("MW_AGENT_SERVICE", "localhost");
}
