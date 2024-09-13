/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class NoopTest {
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void noopInstance() {
    Meter meter = testing.getOpenTelemetry().getMeter("test");

    LongCounterBuilder counterBuilder = meter.counterBuilder("test");
    assertThat(counterBuilder).isNotInstanceOf(ExtendedLongCounterBuilder.class);

    LongUpDownCounterBuilder upDownCounterBuilder = meter.upDownCounterBuilder("test");
    assertThat(upDownCounterBuilder).isNotInstanceOf(ExtendedLongUpDownCounterBuilder.class);

    DoubleGaugeBuilder gaugeBuilder = meter.gaugeBuilder("test");
    assertThat(gaugeBuilder).isNotInstanceOf(ExtendedDoubleGaugeBuilder.class);

    DoubleHistogramBuilder histogramBuilder = meter.histogramBuilder("test");
    assertThat(histogramBuilder).isNotInstanceOf(ExtendedDoubleHistogramBuilder.class);
  }
}
