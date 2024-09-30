package com.example.javaagent.config;

import java.util.Map;
import java.util.function.Function;

public class EnvironmentConfig {

  private static final Map<String, String> ENV = System.getenv();

  // Enum for environment variables
  public enum EnvVar {
    MW_PROFILING_SERVER_URL(null),
    MW_PROFILING_ALLOC("512k"),
    MW_PROFILING_LOCK("10ms"),
    MW_AGENT_SERVICE("localhost"),
    MW_AUTH_URL("https://app.middleware.io/api/v1/auth"),
    MW_APM_COLLECT_PROFILING("true"),
    MW_APM_COLLECT_TRACES("true"),
    MW_APM_COLLECT_LOGS("true"),
    MW_APM_COLLECT_METRICS("true"),
    MW_DISABLE_TELEMETRY("true"),
    MW_TARGET(""),
    MW_API_KEY(null),
    MW_PROPAGATORS("b3"),
    MW_CUSTOM_RESOURCE_ATTRIBUTE(null),
    MW_LOG_LEVEL(null);

    private final String defaultValue;

    EnvVar(String defaultValue) {
      this.defaultValue = defaultValue;
    }
  }

  public static String get(EnvVar var) {
    return ENV.getOrDefault(var.name(), var.defaultValue);
  }

  public static boolean getBoolean(EnvVar var) {
    String value = get(var);
    return value != null && value.toLowerCase().matches("true|1|t|y|yes");
  }

  public static String getEnvConfigValue(String otelKey, String mwKey) {
    return ENV.getOrDefault(otelKey, ENV.getOrDefault(mwKey, ""));
  }

  // Utility method to get environment variable with custom parsing
  public static <T> T get(EnvVar var, Function<String, T> parser) {
    String value = get(var);
    return value != null ? parser.apply(value) : null;
  }

  // Public getters for all environment variables
  public static String getMwProfilingServerUrl() {
    return get(EnvVar.MW_PROFILING_SERVER_URL);
  }

  public static String getMwProfilingAlloc() {
    return get(EnvVar.MW_PROFILING_ALLOC);
  }

  public static String getMwProfilingLock() {
    return get(EnvVar.MW_PROFILING_LOCK);
  }

  public static String getMwAgentService() {
    return get(EnvVar.MW_AGENT_SERVICE);
  }

  public static String getMwAuthUrl() {
    return get(EnvVar.MW_AUTH_URL);
  }

  public static boolean isMwApmCollectProfiling() {
    return getBoolean(EnvVar.MW_APM_COLLECT_PROFILING);
  }

  public static boolean isMwApmCollectTraces() {
    return getBoolean(EnvVar.MW_APM_COLLECT_TRACES);
  }

  public static boolean isMwApmCollectLogs() {
    return getBoolean(EnvVar.MW_APM_COLLECT_LOGS);
  }

  public static boolean isMwApmCollectMetrics() {
    return getBoolean(EnvVar.MW_APM_COLLECT_METRICS);
  }

  public static boolean isMwDisableTelemetry() {
    return getBoolean(EnvVar.MW_DISABLE_TELEMETRY);
  }

  public static String getMwTarget() {
    return get(EnvVar.MW_TARGET);
  }

  public static String getMwApiKey() {
    return get(EnvVar.MW_API_KEY);
  }

  public static String getMwPropagators() {
    return get(EnvVar.MW_PROPAGATORS);
  }

  public static String getMwCustomResourceAttribute() {
    return get(EnvVar.MW_CUSTOM_RESOURCE_ATTRIBUTE);
  }

  public static String getMwLogLevel() {
    return get(EnvVar.MW_LOG_LEVEL);
  }
}
