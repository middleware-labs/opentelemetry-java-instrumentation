/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import java.util.List;
import javax.annotation.Nullable;

class AkkaHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getUrlFull(HttpRequest httpRequest) {
    return httpRequest.uri().toString();
  }

  @Override
  public String getHttpRequestMethod(HttpRequest httpRequest) {
    return httpRequest.method().value();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest httpRequest, String name) {
    return AkkaHttpUtil.requestHeader(httpRequest, name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest httpRequest, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.status().intValue();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest httpRequest, HttpResponse httpResponse, String name) {
    return AkkaHttpUtil.responseHeader(httpResponse, name);
  }
}
