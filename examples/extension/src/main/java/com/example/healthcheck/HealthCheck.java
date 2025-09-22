/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.healthcheck;

import com.example.javaagent.config.EnvironmentConfig;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HealthCheck {
  private static final Logger LOGGER = Logger.getLogger(HealthCheck.class.getName());
  private static final int TIMEOUT_MS = 5000;
  private static final String DEFAULT_HOST = "localhost";
  private static final String HEALTH_CHECK_PATH = "/healthcheck";
  private static final int HEALTH_CHECK_PORT = 13133;

  private final String healthCheckUrl;

  public HealthCheck() {
    this.healthCheckUrl = buildHealthCheckUrl();
  }

  public boolean isHealthy() {
    try {
      boolean isHealthy = performHealthCheck();
      logHealthCheckResult(isHealthy);
      return isHealthy;
    } catch (IOException e) {
      String mwTarget = EnvironmentConfig.getMwTarget();

      if (mwTarget == null || mwTarget.trim().isEmpty()) {
        LOGGER.log(
            Level.WARNING,
            "Health check failed due to I/O error. No monitoring agent detected and target URL is not configured. "
                + "Instrumentation will continue but functionality may be degraded. Please verify network connectivity "
                + "and monitoring configuration.",
            e);
        return false;
      } else {
        return true;
      }
    } catch (Exception e) {
      LOGGER.log(
          Level.SEVERE,
          "Unexpected error during health check. Instrumentation status unknown and may be unreliable.",
          e);
      return false;
    }
  }

  private String buildHealthCheckUrl() {
    String host = EnvironmentConfig.getMwAgentService();
    if (host == null || host.trim().isEmpty()) {
      host = DEFAULT_HOST;
    }
    return String.format("http://%s:%d%s", host, HEALTH_CHECK_PORT, HEALTH_CHECK_PATH);
  }

  private boolean performHealthCheck() throws IOException {
    HttpURLConnection connection = null;
    try {
      URL url = new URL(healthCheckUrl);
      connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("GET");
      connection.setConnectTimeout(TIMEOUT_MS);
      connection.setReadTimeout(TIMEOUT_MS);

      int responseCode = connection.getResponseCode();
      return responseCode >= 200 && responseCode < 300;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  public void logHealthCheckResult(boolean isHealthy) {
    if (isHealthy) {
      LOGGER.info("Health check passed. Proceeding with instrumentation.");
    } else if (EnvironmentConfig.getMwTarget() == "") {
      LOGGER.log(
          Level.WARNING,
          "MW Agent Healthcheck is failing ... This could be due to incorrect value of MW_AGENT_SERVICE\n"
              + "Ignore the warning if you are using MW Agent older than 1.7.7 (You can confirm by running `mw-agent version`)");
    }
  }
}
