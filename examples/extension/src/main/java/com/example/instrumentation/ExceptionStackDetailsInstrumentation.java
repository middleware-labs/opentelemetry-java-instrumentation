/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExceptionStackDetailsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpan");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // Intercept recordException(Throwable throwable, Attributes attributes)
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("recordException"))
            .and(takesArguments(2))
            .and(takesArgument(0, Throwable.class))
            .and(takesArgument(1, Attributes.class)),
        ExceptionStackDetailsInstrumentation.class.getName() + "$RecordExceptionWithAttributesAdvice");
    
    // Intercept recordException(Throwable throwable) - single parameter version
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("recordException"))
            .and(takesArguments(1))
            .and(takesArgument(0, Throwable.class)),
        ExceptionStackDetailsInstrumentation.class.getName() + "$RecordExceptionAdvice");
  }

  @SuppressWarnings("unused")
  public static class RecordExceptionWithAttributesAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(value = 0) Throwable throwable, 
                               @Advice.Argument(value = 1, readOnly = false) Attributes attributes) {
      if (throwable == null) {
        return;
      }

      try {
        // Generate stack details for the throwable
        String stackDetails = generateStackDetails(throwable);
        if (stackDetails != null && !stackDetails.isEmpty()) {
          // Create new attributes builder with existing attributes
          AttributesBuilder attributesBuilder = attributes.toBuilder();
          
          // Add our custom stack details attributes
          attributesBuilder.put("exception.stack_details", stackDetails);
          
          // Replace the attributes parameter with our enhanced version
          attributes = attributesBuilder.build();
        }
      } catch (Exception e) {
        // Fail silently to avoid breaking the application
        System.err.println("Failed to add exception stack details: " + e.getMessage());
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RecordExceptionAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, skipOn = Advice.OnNonDefaultValue.class)
    public static boolean onEnter(@Advice.This Object applicationSpan, 
                                  @Advice.Argument(0) Throwable throwable) {
      if (throwable == null) {
        return false;
      }

      try {
        // Generate stack details for the throwable
        String stackDetails = generateStackDetails(throwable);
        if (stackDetails != null && !stackDetails.isEmpty()) {
          // Cast to ApplicationSpan and call the two-parameter version with our attributes
          if (applicationSpan instanceof io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpan) {
            io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpan appSpan = 
                (io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.ApplicationSpan) applicationSpan;
            
            // Create attributes with our stack details
            AttributesBuilder attributesBuilder = Attributes.builder();
            attributesBuilder.put("exception.stack_details", stackDetails);
            Attributes enhancedAttributes = attributesBuilder.build();
            
            // Call the two-parameter version which will be enhanced by our other advice
            appSpan.recordException(throwable, enhancedAttributes);
            
            // Skip the original single-parameter method execution
            return true;
          }
        }
      } catch (Exception e) {
        // Fail silently to avoid breaking the application  
        System.err.println("Failed to add exception stack details: " + e.getMessage());
      }
      
      return false; // Continue with original method execution
    }
  }

    private static String generateStackDetails(Throwable throwable) {
      List<Map<String, Object>> stackDetailsList = new ArrayList<>();
      
      for (StackTraceElement element : throwable.getStackTrace()) {
        Map<String, Object> frameDetails = new HashMap<>();
        
        // Extract basic information from stack trace element
        frameDetails.put("exception.function_name", element.getMethodName());
        frameDetails.put("exception.line", element.getLineNumber());
        frameDetails.put("exception.language", "java");
        
        String className = element.getClassName();
        String fileName = element.getFileName();
        
        if (fileName != null) {
          frameDetails.put("exception.file", fileName);
          
          // Try to determine if the file is external
          boolean isExternal = isExternalFile(className);
          frameDetails.put("exception.is_file_external", isExternal);
          
          // If it's an internal file, try to extract function body
          if (!isExternal) {
            String functionBody = extractFunctionBody(className, element.getMethodName());
            if (functionBody != null) {
              frameDetails.put("exception.function_body", functionBody);
              
              // Try to extract start and end lines (this is simplified)
              int[] lines = extractFunctionLines(className, element.getMethodName());
              if (lines != null) {
                frameDetails.put("exception.start_line", lines[0]);
                frameDetails.put("exception.end_line", lines[1]);
              }
            }
          }
        }
        
        stackDetailsList.add(frameDetails);
      }
      
      // Convert to JSON-like string format
      return convertToJsonString(stackDetailsList);
    }
    
    private static boolean isExternalFile(String className) {
      // Consider anything not in user's package as external
      return className.startsWith("java.") || 
             className.startsWith("javax.") ||
             className.startsWith("sun.") ||
             className.startsWith("com.sun.") ||
             className.startsWith("org.springframework.") ||
             className.startsWith("io.opentelemetry.") ||
             className.startsWith("net.bytebuddy.");
    }
    
    private static String extractFunctionBody(String className, String methodName) {
      try {
        // This is a simplified implementation - in practice, you might want to use
        // ASM or other bytecode analysis tools for more accurate parsing
        String classPath = System.getProperty("java.class.path");
        String[] paths = classPath.split(File.pathSeparator);
        
        for (String path : paths) {
          if (path.endsWith(".jar")) {
            continue; // Skip JAR files for simplicity
          }
          
          String fileName = className.replace('.', File.separatorChar) + ".java";
          File sourceFile = new File(path, fileName);
          
          if (sourceFile.exists()) {
            return readMethodFromFile(sourceFile, methodName);
          }
        }
      } catch (Exception e) {
        // Fail silently
      }
      return null;
    }
    
    private static String readMethodFromFile(File sourceFile, String methodName) {
      try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile))) {
        StringBuilder methodBody = new StringBuilder();
        String line;
        boolean inMethod = false;
        int braceCount = 0;
        
        while ((line = reader.readLine()) != null) {
          if (!inMethod && line.contains(methodName + "(")) {
            inMethod = true;
            methodBody.append(line).append("\n");
            braceCount += countBraces(line, '{') - countBraces(line, '}');
          } else if (inMethod) {
            methodBody.append(line).append("\n");
            braceCount += countBraces(line, '{') - countBraces(line, '}');
            
            if (braceCount <= 0) {
              break;
            }
          }
        }
        
        return methodBody.toString().trim();
      } catch (IOException e) {
        return null;
      }
    }
    
    private static int countBraces(String line, char brace) {
      int count = 0;
      for (char c : line.toCharArray()) {
        if (c == brace) count++;
      }
      return count;
    }
    
    private static int[] extractFunctionLines(String className, String methodName) {
      // This is a simplified implementation
      // In practice, you'd want to use proper source code parsing
      return new int[]{1, 10}; // Placeholder values
    }
    
    private static String convertToJsonString(List<Map<String, Object>> stackDetailsList) {
      StringBuilder json = new StringBuilder();
      json.append("[");
      
      for (int i = 0; i < stackDetailsList.size(); i++) {
        if (i > 0) json.append(",");
        json.append("{");
        
        Map<String, Object> frame = stackDetailsList.get(i);
        boolean first = true;
        for (Map.Entry<String, Object> entry : frame.entrySet()) {
          if (!first) json.append(",");
          json.append("\"").append(entry.getKey()).append("\":");
          
          Object value = entry.getValue();
          if (value instanceof String) {
            json.append("\"").append(escapeJson((String) value)).append("\"");
          } else if (value instanceof Number) {
            json.append(value);
          } else if (value instanceof Boolean) {
            json.append(value);
          } else {
            json.append("\"").append(value != null ? escapeJson(value.toString()) : "null").append("\"");
          }
          first = false;
        }
        
        json.append("}");
      }
      
      json.append("]");
      return json.toString();
    }
    
    private static String escapeJson(String str) {
      if (str == null) return "";
      return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
} 