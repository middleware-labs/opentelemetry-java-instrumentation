/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.config;

/** Access all the system properties. */
public final class SystemProperties {

  public static final String SERVICE_NAME = System.getProperty("otel.service.name");
}
