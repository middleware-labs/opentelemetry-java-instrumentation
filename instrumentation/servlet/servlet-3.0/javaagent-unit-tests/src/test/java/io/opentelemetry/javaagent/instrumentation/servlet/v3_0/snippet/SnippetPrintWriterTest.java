/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet;

import static io.opentelemetry.javaagent.instrumentation.servlet.v3_0.snippet.TestUtil.readFileAsString;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.junit.jupiter.api.Test;

class SnippetPrintWriterTest {

  @Test
  void testInjectToTextHtml() throws IOException {
    String snippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String html = readFileAsString("beforeSnippetInjection.html");

    InMemoryHttpServletResponse response = createInMemoryHttpServletResponse("text/html");
    Servlet3SnippetInjectingResponseWrapper responseWrapper =
        new Servlet3SnippetInjectingResponseWrapper(response, snippet);

    responseWrapper.getWriter().write(html);
    responseWrapper.getWriter().flush();

    String expectedHtml = readFileAsString("afterSnippetInjection.html");
    assertThat(response.getStringContent()).isEqualTo(expectedHtml);
  }

  @Test
  void testInjectToChineseTextHtml() throws IOException {
    String snippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String html = readFileAsString("beforeSnippetInjectionChinese.html");

    InMemoryHttpServletResponse response = createInMemoryHttpServletResponse("text/html");
    Servlet3SnippetInjectingResponseWrapper responseWrapper =
        new Servlet3SnippetInjectingResponseWrapper(response, snippet);

    responseWrapper.getWriter().write(html);
    responseWrapper.getWriter().flush();

    String expectedHtml = readFileAsString("afterSnippetInjectionChinese.html");
    assertThat(response.getStringContent()).isEqualTo(expectedHtml);
  }

  @Test
  void shouldNotInjectToTextHtml() throws IOException {
    String snippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String html = readFileAsString("beforeSnippetInjection.html");

    InMemoryHttpServletResponse response = createInMemoryHttpServletResponse("not/text");

    Servlet3SnippetInjectingResponseWrapper responseWrapper =
        new Servlet3SnippetInjectingResponseWrapper(response, snippet);

    responseWrapper.getWriter().write(html);
    responseWrapper.getWriter().flush();

    assertThat(response.getStringContent()).isEqualTo(html);
  }

  @Test
  void testWriteInt() throws IOException {
    String snippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String html = readFileAsString("beforeSnippetInjection.html");

    InMemoryHttpServletResponse response = createInMemoryHttpServletResponse("text/html");
    Servlet3SnippetInjectingResponseWrapper responseWrapper =
        new Servlet3SnippetInjectingResponseWrapper(response, snippet);

    byte[] originalBytes = html.getBytes(Charset.defaultCharset());
    for (byte originalByte : originalBytes) {
      responseWrapper.getWriter().write(originalByte);
    }
    responseWrapper.getWriter().flush();

    String expectedHtml = readFileAsString("afterSnippetInjection.html");
    assertThat(response.getStringContent()).isEqualTo(expectedHtml);
  }

  @Test
  void testWriteCharArray() throws IOException {
    String snippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String html = readFileAsString("beforeSnippetInjectionChinese.html");

    InMemoryHttpServletResponse response = createInMemoryHttpServletResponse("text/html");
    Servlet3SnippetInjectingResponseWrapper responseWrapper =
        new Servlet3SnippetInjectingResponseWrapper(response, snippet);

    char[] originalChars = html.toCharArray();
    responseWrapper.getWriter().write(originalChars, 0, originalChars.length);
    responseWrapper.getWriter().flush();

    String expectedHtml = readFileAsString("afterSnippetInjectionChinese.html");
    assertThat(response.getStringContent()).isEqualTo(expectedHtml);
  }

  @Test
  void testWriteWithOffset() throws IOException {
    String snippet = "\n  <script type=\"text/javascript\"> Test </script>";
    String html = readFileAsString("beforeSnippetInjectionChinese.html");
    String extraBuffer = "this buffer should not be print out";
    html = extraBuffer + html;

    InMemoryHttpServletResponse response = createInMemoryHttpServletResponse("text/html");
    Servlet3SnippetInjectingResponseWrapper responseWrapper =
        new Servlet3SnippetInjectingResponseWrapper(response, snippet);

    responseWrapper
        .getWriter()
        .write(html, extraBuffer.length(), html.length() - extraBuffer.length());
    responseWrapper.getWriter().flush();

    String expectedHtml = readFileAsString("afterSnippetInjectionChinese.html");
    assertThat(response.getStringContent()).isEqualTo(expectedHtml);
  }

  private static InMemoryHttpServletResponse createInMemoryHttpServletResponse(String contentType) {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getContentType()).thenReturn(contentType);
    when(response.getStatus()).thenReturn(200);
    when(response.containsHeader("content-type")).thenReturn(true);
    return new InMemoryHttpServletResponse(response);
  }

  private static class InMemoryHttpServletResponse extends HttpServletResponseWrapper {

    private PrintWriter printWriter;
    private StringWriter stringWriter;

    InMemoryHttpServletResponse(HttpServletResponse delegate) {
      super(delegate);
    }

    @Override
    public PrintWriter getWriter() {
      if (printWriter == null) {
        stringWriter = new StringWriter();
        printWriter = new PrintWriter(stringWriter);
      }
      return printWriter;
    }

    String getStringContent() {
      printWriter.flush();
      return stringWriter.toString();
    }
  }
}
