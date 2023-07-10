/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.config;

import java.util.regex.Pattern;

/** Pre compiled Regular expressions. */
public final class Patterns {

  public static final Pattern TARGET_URL_PATTERN =
      Pattern.compile(
          "^https:[\\/]{2}[a-z0-9]{5,7}.(stage.env.|agent.env.|front.env.|conflux.env.|capture.env.){0,1}middleware.io:443");

  public static final Pattern TENANT_ID_PATTERN = Pattern.compile("^https:\\/\\/([a-z0-9]+)\\.");
}
