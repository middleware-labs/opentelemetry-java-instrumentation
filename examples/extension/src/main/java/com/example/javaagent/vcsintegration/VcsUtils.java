/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent.vcsintegration;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Logger;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;

public class VcsUtils {
  private static final Logger LOGGER = Logger.getLogger(VcsUtils.class.getName());

  /**
   * Gets the current commit SHA by searching for .git directory starting from current directory and
   * moving up the directory tree
   */
  public static String getCurrentCommitSha() {
    try {
      Repository repository = findRepository();
      if (repository == null) {
        LOGGER.info("No Git repository found");
        return null;
      }

      ObjectId head = repository.resolve("HEAD");
      if (head == null) {
        LOGGER.warning("No HEAD commit found in repository");
        return null;
      }

      String commitSha = head.getName();
      LOGGER.info("Found commit SHA: " + commitSha);
      repository.close();
      return commitSha;

    } catch (IOException e) {
      LOGGER.warning("Error reading Git commit SHA: " + e.getMessage());
      return null;
    }
  }

  /** Gets the repository URL by searching for .git directory and reading the remote origin URL */
  public static String getRepositoryUrl() {
    try {
      Repository repository = findRepository();
      if (repository == null) {
        LOGGER.info("No Git repository found");
        return null;
      }

      Set<String> remoteNames = repository.getRemoteNames();
      if (remoteNames.isEmpty()) {
        LOGGER.warning("No remote repositories found");
        repository.close();
        return null;
      }

      // Try to find 'origin' remote first, fallback to first available
      String remoteName = remoteNames.contains("origin") ? "origin" : remoteNames.iterator().next();

      RemoteConfig remoteConfig = new RemoteConfig(repository.getConfig(), remoteName);
      if (remoteConfig.getURIs().isEmpty()) {
        LOGGER.warning("No URIs found for remote: " + remoteName);
        repository.close();
        return null;
      }

      URIish uri = remoteConfig.getURIs().get(0);
      String repositoryUrl = cleanRepositoryUrl(uri.toString());
      LOGGER.info("Found repository URL: " + repositoryUrl);
      repository.close();
      return repositoryUrl;

    } catch (Exception e) {
      LOGGER.warning("Error reading Git repository URL: " + e.getMessage());
      return null;
    }
  }

  /**
   * Searches for Git repository using JGit's built-in discovery mechanism which scans environment
   * variables and searches up the file system tree
   */
  private static Repository findRepository() throws IOException {
    try {
      FileRepositoryBuilder builder = new FileRepositoryBuilder();
      Repository repo = builder.readEnvironment().findGitDir().build();

      LOGGER.info("Found Git repository at: " + repo.getDirectory().getAbsolutePath());
      return repo;
    } catch (IllegalArgumentException e) {
      LOGGER.info("No Git repository found: " + e.getMessage());
      return null;
    }
  }

  /** Cleans the repository URL by removing .git suffix as per requirements */
  private static String cleanRepositoryUrl(String url) {
    if (url == null || url.isEmpty()) {
      return url;
    }

    // Remove .git suffix if present
    if (url.endsWith(".git")) {
      return url.substring(0, url.length() - 4);
    }

    return url;
  }
}
