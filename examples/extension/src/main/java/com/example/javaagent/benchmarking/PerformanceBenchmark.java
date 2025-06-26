/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.benchmarking;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class PerformanceBenchmark {
  private static final Logger LOGGER = Logger.getLogger(PerformanceBenchmark.class.getName());

  // Performance counters
  private static final AtomicLong totalExportTime = new AtomicLong(0);
  private static final AtomicLong totalClassificationTime = new AtomicLong(0);
  private static final AtomicLong totalSourceReadTime = new AtomicLong(0);
  private static final AtomicLong totalMethodExtractionTime = new AtomicLong(0);
  private static final AtomicLong totalJsonSerializationTime = new AtomicLong(0);
  private static final AtomicInteger totalExports = new AtomicInteger(0);
  private static final AtomicInteger totalClassifications = new AtomicInteger(0);
  private static final AtomicInteger totalSourceReads = new AtomicInteger(0);
  private static final AtomicInteger totalMethodExtractions = new AtomicInteger(0);

  public static void recordExportTime(long timeMs) {
    totalExportTime.addAndGet(timeMs);
    totalExports.incrementAndGet();
  }

  public static void recordClassificationTime(long timeMs) {
    totalClassificationTime.addAndGet(timeMs);
    totalClassifications.incrementAndGet();
  }

  public static void recordSourceReadTime(long timeMs) {
    totalSourceReadTime.addAndGet(timeMs);
    totalSourceReads.incrementAndGet();
  }

  public static void recordMethodExtractionTime(long timeMs) {
    totalMethodExtractionTime.addAndGet(timeMs);
    totalMethodExtractions.incrementAndGet();
  }

  public static void recordJsonSerializationTime(long timeMs) {
    totalJsonSerializationTime.addAndGet(timeMs);
  }

  public static void printStats() {
    LOGGER.info("=== PERFORMANCE BENCHMARK RESULTS ===");
    LOGGER.info("Total Exports: " + totalExports.get());

    if (totalExports.get() > 0) {
      LOGGER.info("Average Export Time: " + (totalExportTime.get() / totalExports.get()) + " ms");
    }

    if (totalClassifications.get() > 0) {
      LOGGER.info("Total Classifications: " + totalClassifications.get());
      LOGGER.info(
          "Average Classification Time: "
              + (totalClassificationTime.get() / totalClassifications.get())
              + " ms");
      LOGGER.info("Total Classification Time: " + totalClassificationTime.get() + " ms");
    }

    if (totalSourceReads.get() > 0) {
      LOGGER.info("Total Source Reads: " + totalSourceReads.get());
      LOGGER.info(
          "Average Source Read Time: "
              + (totalSourceReadTime.get() / totalSourceReads.get())
              + " ms");
      LOGGER.info("Total Source Read Time: " + totalSourceReadTime.get() + " ms");
    }

    if (totalMethodExtractions.get() > 0) {
      LOGGER.info("Total Method Extractions: " + totalMethodExtractions.get());
      LOGGER.info(
          "Average Method Extraction Time: "
              + (totalMethodExtractionTime.get() / totalMethodExtractions.get())
              + " ms");
      LOGGER.info("Total Method Extraction Time: " + totalMethodExtractionTime.get() + " ms");
    }

    LOGGER.info("Total JSON Serialization Time: " + totalJsonSerializationTime.get() + " ms");
    LOGGER.info("=====================================");
  }

  public static void reset() {
    totalExportTime.set(0);
    totalClassificationTime.set(0);
    totalSourceReadTime.set(0);
    totalMethodExtractionTime.set(0);
    totalJsonSerializationTime.set(0);
    totalExports.set(0);
    totalClassifications.set(0);
    totalSourceReads.set(0);
    totalMethodExtractions.set(0);
  }
}
