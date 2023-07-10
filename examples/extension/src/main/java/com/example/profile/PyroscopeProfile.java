/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.profile;

import com.example.javaagent.config.EnvironmentConfig;
import com.example.javaagent.config.Patterns;
import com.example.javaagent.config.SystemProperties;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import java.util.regex.Matcher;

/** Pyrosope profiling. */
public class PyroscopeProfile {

  /** Static method to start profiling. */
  public static void startProfiling() {
    PyroscopeAgent.start(
        new Config.Builder()
            .setApplicationName(SystemProperties.SERVICE_NAME)
            .setProfilingEvent(EventType.ITIMER)
            .setAllocLive(true)
            .setServerAddress(EnvironmentConfig.MW_PROFILING_SERVER_URL)
            .setTenantID(getTenantId(EnvironmentConfig.TARGET))
            .build());
  }

  /**
   * Validate the target URL.
   *
   * @param target target URL string.
   * @return validation result as boolean
   */
  private static boolean verifyTargetURL(String target) {
    if (target == null) {
      throw new RuntimeException("Environment variable TARGET is undefined");
    }
    Matcher matcher = Patterns.TARGET_URL_PATTERN.matcher(target);
    if (!matcher.find()) {
      throw new RuntimeException("Environment variable TARGET is incorrect");
    }
    return true;
  }

  /**
   * Verify the target URL and provides tenant id based on the target URL.
   *
   * @param target target URL string.
   * @return tanent id of account.
   */
  private static String getTenantId(String target) {
    verifyTargetURL(target);
    Matcher matcher = Patterns.TENANT_ID_PATTERN.matcher(target);
    matcher.find();
    return matcher.group(1);
  }
}
