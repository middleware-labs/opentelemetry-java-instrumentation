/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1

import io.opentelemetry.instrumentation.api.internal.HttpConstants
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.restlet.Application
import org.restlet.Restlet
import org.restlet.Router

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.CAPTURE_HEADERS
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.INDEXED_CHILD
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.QUERY_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

abstract class AbstractServletServerTest extends HttpServerTest<Server> {

  @Override
  Server startServer(int port) {

    def webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())

    webAppContext.setBaseResource(Resource.newSystemResource("servlet-ext-app"))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  boolean testException() {
    false
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    if (method == HttpConstants._OTHER) {
      return getContextPath() + endpoint.path
    }
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return getContextPath() + "/*"
      default:
        return super.expectedHttpRoute(endpoint, method)
    }
  }

  @Override
  int getResponseCodeOnNonStandardHttpMethod() {
    405
  }

  static class TestApp extends Application {

    @Override
    Restlet createRoot() {
      def router = new Router(getContext())

      router.attach(SUCCESS.path, RestletAppTestBase.SuccessResource)
      router.attach(REDIRECT.path, RestletAppTestBase.RedirectResource)
      router.attach(ERROR.path, RestletAppTestBase.ErrorResource)
      router.attach(EXCEPTION.path, RestletAppTestBase.ExceptionResource)
      router.attach("/path/{id}/param", RestletAppTestBase.PathParamResource)
      router.attach(QUERY_PARAM.path, RestletAppTestBase.QueryParamResource)
      router.attach(CAPTURE_HEADERS.path, RestletAppTestBase.CaptureHeadersResource)
      router.attach(INDEXED_CHILD.path, RestletAppTestBase.IndexedChildResource)

      return router
    }

  }
}
