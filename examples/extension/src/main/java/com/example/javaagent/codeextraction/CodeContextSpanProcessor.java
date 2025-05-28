/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.codeextraction;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.logging.Logger;

public class CodeContextSpanProcessor implements SpanProcessor {
  private static final Logger LOGGER = Logger.getLogger(CodeContextSpanProcessor.class.getName());

  @Override
  public void onStart(Context parentContext, ReadWriteSpan span) {
    // Nothing to do on span start
  }

  @Override
  public boolean isStartRequired() {
    return false;
  }

  @Override
  public void onEnd(ReadableSpan span) {
    try {
      if (shouldEnhanceSpan(span)) {
        enhanceSpanWithCodeContext(span);
      }
    } catch (Exception e) {
      LOGGER.warning("Error processing span for code context: " + e.getMessage());
    }
  }

  @Override
  public boolean isEndRequired() {
    return true;
  }

  @Override
  public CompletableResultCode shutdown() {
    return CompletableResultCode.ofSuccess();
  }

  @Override
  public CompletableResultCode forceFlush() {
    return CompletableResultCode.ofSuccess();
  }

  /** Determines if a span should be enhanced with code context */
  private boolean shouldEnhanceSpan(ReadableSpan span) {
    // Check if span has error status
    if (span.toSpanData().getStatus().getStatusCode()
        == io.opentelemetry.api.trace.StatusCode.ERROR) {
      return true;
    }

    // Check if span has exception events
    return span.toSpanData().getEvents().stream()
        .anyMatch(
            event ->
                event.getName().toLowerCase().contains("exception")
                    || event.getName().toLowerCase().contains("error")
                    || event
                            .getAttributes()
                            .get(
                                io.opentelemetry.api.common.AttributeKey.stringKey(
                                    "exception.type"))
                        != null);
  }

  /** Enhances the span with code context information */
  private void enhanceSpanWithCodeContext(ReadableSpan span) {
    try {
      SpanData spanData = span.toSpanData();
      AttributesBuilder attributesBuilder = spanData.getAttributes().toBuilder();

      boolean enhanced = SpanCodeEnhancer.enhanceSpanWithCodeContext(spanData, attributesBuilder);

      if (enhanced) {
        LOGGER.info("Enhanced span with code context: " + spanData.getName());
      }

    } catch (Exception e) {
      LOGGER.warning("Error enhancing span with code context: " + e.getMessage());
    }
  }
}
