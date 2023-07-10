/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

/**
 * See <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/logs/sdk.md#logrecordprocessor">
 * OpenTelemetry Specification</a> for more information about {@link LogRecordProcessor}.
 *
 * @see DemoAutoConfigurationCustomizerProvider
 */
public class DemoLogRecordProcessor implements LogRecordProcessor {

  private static DemoLogRecordProcessor instance;

  static DemoLogRecordProcessor getInstance() {
    if (instance == null) {
      instance = new DemoLogRecordProcessor();
    }
    return instance;
  }

  private DemoLogRecordProcessor() {}

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    logRecord.setAttribute(AttributeKey.stringKey("mw.app.lang"), "java");
  }

  @Override
  public String toString() {
    return "DemoLogRecordProcessor";
  }
}
