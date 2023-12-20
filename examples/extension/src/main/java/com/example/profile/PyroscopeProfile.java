/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.profile;

import com.example.javaagent.config.EnvironmentConfig;
import com.example.javaagent.config.SystemProperties;
import io.pyroscope.javaagent.EventType;
import io.pyroscope.javaagent.PyroscopeAgent;
import io.pyroscope.javaagent.config.Config;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.logging.Logger;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/** Pyroscope profiling. */
public class PyroscopeProfile {

  private static Logger logger = Logger.getLogger("pyroscope-profile");

  /** Static method to start profiling. */
  public static void startProfiling() {
    try {
      String tenantId = authenticateAndGetTenantId();
      if (tenantId != null) {
        PyroscopeAgent.start(
            new Config.Builder()
                .setApplicationName(SystemProperties.SERVICE_NAME)
                .setProfilingEvent(EventType.ITIMER)
                .setProfilingAlloc(EnvironmentConfig.MW_PROFILING_ALLOC)
                .setProfilingLock(EnvironmentConfig.MW_PROFILING_LOCK)
                .setServerAddress(EnvironmentConfig.MW_PROFILING_SERVER_URL)
                .setTenantID(tenantId)
                .build());
      } else {
        logger.warning("Profiling is not initiated as authentication is failed");
      }
    } catch (Exception e) {
      logger.warning("Something went wrong while initialization of profiling");
      e.printStackTrace();
    }
  }

  /**
   * Authenticate with MW platform.
   *
   * @return tenant id of respective account.
   */
  private static String authenticateAndGetTenantId() {
    try {
      // Data validation
      String mwApiKey = EnvironmentConfig.MW_API_KEY;
      if (mwApiKey == null) {
        logger.warning(
            "Profiling is not initiated as environment variable MW_API_KEY is not provided");
        return null;
      }

      // Build Request
      HttpPost authRequest = new HttpPost(URI.create(EnvironmentConfig.MW_AUTH_URL));
      authRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");
      authRequest.addHeader("Authorization", "Bearer " + mwApiKey);

      // Send authentication request
      CloseableHttpClient httpClient = HttpClients.createDefault();
      CloseableHttpResponse response = httpClient.execute(authRequest);

      // Get response in string
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      response.getEntity().writeTo(outputStream);
      InputStreamReader inputStreamReader =
          new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()));
      BufferedReader reader = new BufferedReader(inputStreamReader);
      StringBuilder stringBuilder = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        stringBuilder.append(line);
      }

      // Parse the response string and extract the tenant id
      JSONParser parser = new JSONParser();
      JSONObject jsonResponse = (JSONObject) parser.parse(stringBuilder.toString());
      JSONObject jsonData = (JSONObject) jsonResponse.getOrDefault("data", null);
      if (jsonData == null) {
        return null;
      }

      return (String) jsonData.getOrDefault("account", null);
    } catch (Exception e) {
      logger.severe(e.getMessage());
      e.printStackTrace();
    }
    return null;
  }
}
