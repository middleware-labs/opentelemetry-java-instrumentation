# Improvement Action Plan

This document provides a prioritized, actionable plan for implementing the improvements identified in the Technical Debt Report.

## Quick Reference

| Priority | Action | Effort | Impact | Status |
|----------|--------|--------|--------|--------|
| 🔴 HIGH | Update GitHub Actions workflow | 1 hour | High | ✅ DONE |
| 🔴 HIGH | Run security audit (OWASP) | 2 hours | High | TODO |
| 🟡 MEDIUM | Document build workarounds | 2 hours | Medium | ✅ DONE |
| 🟡 MEDIUM | Create TODO tracking issues | 4 hours | Medium | TODO |
| 🟡 MEDIUM | Update Mockito to v5 | 3 hours | Medium | TODO |
| 🟢 LOW | Enable line length checks | 2 hours | Low | TODO |
| 🟢 LOW | Add coverage reporting | 3 hours | Medium | TODO |

---

## Phase 1: Critical Updates (Week 1-2)

### ✅ COMPLETED: Update GitHub Actions Workflow

**File:** `.github/workflows/middleware-javaagent.yml`

**Changes Applied:**
- ✅ Updated `actions/checkout@v2` → `actions/checkout@v4`
- ✅ Updated `actions/setup-java@v2` → `actions/setup-java@v4`
- ✅ Updated Java version from 17 → 21
- ✅ Updated distribution from 'adopt' → 'temurin'
- ✅ Replaced deprecated `actions/create-release@v1` with `softprops/action-gh-release@v2`
- ✅ Replaced deprecated `actions/upload-release-asset@v1` (now handled by action-gh-release)
- ✅ Added `fail_on_unmatched_files: true` for better error handling

**Testing:**
```bash
# Verify workflow syntax
gh workflow view "Create Release"

# Test locally with act (if installed)
act -l
```

---

### TODO: Security Audit

**Task:** Run OWASP Dependency Check

**Command:**
```bash
./gradlew dependencyCheckAnalyze
```

**Review:** Check `build/reports/dependency-check-report.html` for vulnerabilities

**Action Items:**
1. Review all HIGH and CRITICAL vulnerabilities
2. Update vulnerable dependencies where possible
3. Add suppressions for false positives
4. Document accepted risks

**Estimated Time:** 2-3 hours

---

### ✅ COMPLETED: Build System Documentation

**File:** `docs/architecture/BUILD_SYSTEM.md`

**Content Created:**
- Gradle properties explanation
- Plugin workarounds documentation
- Java version management
- Common build tasks reference
- Troubleshooting guide
- Performance tips

**Next Steps:**
- Link from main CONTRIBUTING.md
- Add to documentation index
- Share with team for review

---

## Phase 2: Medium Priority Tasks (Week 3-4)

### TODO: Create GitHub Issues for Build TODOs

**Objective:** Convert inline TODO comments to tracked GitHub issues

**Process:**
1. Extract all TODO comments from build files:
```bash
grep -r "TODO" --include="*.gradle.kts" . | grep -v "build/" | tee todos.txt
```

2. Categorize TODOs:
   - JMH configuration issues
   - Testing improvements
   - Refactoring opportunities
   - Workarounds that need proper fixes

3. Create GitHub issues with template:
   ```markdown
   ## Context
   [Location of TODO]
   
   ## Current Situation
   [What the TODO says]
   
   ## Proposed Solution
   [What should be done]
   
   ## Impact
   [Why this matters]
   ```

4. Label appropriately: `technical-debt`, `build-system`, `good-first-issue`

**Expected Issues:** ~20-25 issues

**Estimated Time:** 4 hours

---

### TODO: Update Mockito to v5

**Current:** 4.11.0  
**Target:** 5.14.2 (or latest 5.x)

**Steps:**

1. **Research Breaking Changes:**
   ```bash
   # Review Mockito 5.0 changelog
   open https://github.com/mockito/mockito/releases/tag/v5.0.0
   ```

2. **Update Dependency:**
   ```kotlin
   // In dependencyManagement/build.gradle.kts
   val mockitoVersion = "5.14.2"
   ```

3. **Test Impact:**
   ```bash
   # Run all tests
   ./gradlew test
   
   # Check for failures
   ./gradlew test --continue | tee test-results.txt
   ```

4. **Fix Breaking Changes:**
   - Update mock annotations if needed
   - Fix any changed APIs
   - Review inline mocking requirements

5. **Verify:**
   ```bash
   # Run full test suite
   ./gradlew clean test
   ```

**Risk:** MEDIUM (Mockito 5 has breaking changes)  
**Estimated Time:** 3-4 hours  
**Fallback:** Stay on 4.x if too many issues

---

### TODO: Dependency Version Audit

**Objective:** Review and update all dependencies

**Command:**
```bash
# Generate dependency report
./gradlew dependencyUpdates

# Or use Renovate dashboard
# Check: https://github.com/[org]/[repo]/pulls?q=is%3Apr+author%3Aapp%2Frenovate
```

**Review List:**
- [ ] ByteBuddy: 1.15.1 → 1.15.4
- [ ] ErrorProne: 2.31.0 → 2.33.0
- [ ] Groovy: 4.0.22 → 4.0.24
- [ ] JUnit: 5.11.0 → 5.11.2
- [ ] Kotlin: 2.0.20 → 2.0.21

**Process:**
1. Update versions in `dependencyManagement/build.gradle.kts`
2. Run tests: `./gradlew test`
3. Check for deprecation warnings
4. Commit with message: `chore: update [dependency] to [version]`

**Estimated Time:** 2-3 hours

---

## Phase 3: Code Quality (Week 5-8)

### TODO: Enable Checkstyle Line Length

**File:** `buildscripts/checkstyle.xml`

**Current State:**
```xml
<!--
<module name="LineLength">
  <property name="fileExtensions" value="java"/>
  <property name="max" value="100"/>
  <property name="ignorePattern"
    value="^package.*|^import.*|a href|href|http://|https://|ftp://"/>
</module>
-->
```

**Action Plan:**

1. **Baseline Measurement:**
   ```bash
   # Find lines longer than 100 characters
   find . -name "*.java" -exec awk 'length>100' {} + | wc -l
   ```

2. **Gradual Introduction:**
   - Start with 120 character limit (less disruptive)
   - Add suppressions for specific files
   - Set up auto-formatting in Spotless

3. **Update Configuration:**
   ```xml
   <module name="LineLength">
     <property name="fileExtensions" value="java"/>
     <property name="max" value="120"/>
     <property name="ignorePattern"
       value="^package.*|^import.*|a href|href|http://|https://|ftp://"/>
   </module>
   ```

4. **Fix Existing Violations:**
   ```bash
   # Run Spotless to auto-format where possible
   ./gradlew spotlessApply
   
   # Review remaining violations
   ./gradlew checkstyleMain
   ```

**Estimated Time:** 2-3 hours  
**Recommendation:** Start with 120 chars, gradually reduce to 100

---

### TODO: Centralized Coverage Reporting

**Objective:** Aggregate Jacoco reports across all modules

**Current State:** Per-module reports in `build/reports/jacoco/`

**Solution:** Create aggregated report task

**File:** `build.gradle.kts` (root)

```kotlin
tasks.register<JacocoReport>("jacocoRootReport") {
  description = "Generates an aggregate Jacoco report from all subprojects"
  
  dependsOn(subprojects.map { it.tasks.named("test") })
  
  val reportTasks = subprojects.map { it.tasks.named<JacocoReport>("jacocoTestReport") }
  sourceDirectories.setFrom(files(reportTasks.map { it.get().sourceDirectories }))
  classDirectories.setFrom(files(reportTasks.map { it.get().classDirectories }))
  executionData.setFrom(files(reportTasks.map { it.get().executionData }))
  
  reports {
    xml.required.set(true)
    html.required.set(true)
    xml.outputLocation.set(file("${buildDir}/reports/jacoco/aggregate/jacocoTestReport.xml"))
    html.outputLocation.set(file("${buildDir}/reports/jacoco/aggregate/html"))
  }
}
```

**Usage:**
```bash
./gradlew test jacocoRootReport
open build/reports/jacoco/aggregate/html/index.html
```

**Next Steps:**
- Add coverage badge to README
- Set minimum thresholds (e.g., 70%)
- Integrate with CI/CD

**Estimated Time:** 3 hours

---

### TODO: Refactor Large Files

**Target Files (>1000 lines):**
1. `AbstractGrpcTest.java` (1,701 lines)
2. `ConcurrentLinkedHashMap.java` (1,595 lines)
3. `EnhancedExceptionSpanExporter.java` (1,499 lines)
4. `JdbcConnectionUrlParserTest.java` (1,221 lines)
5. `AbstractHttpClientTest.java` (1,167 lines)

**Strategy:**

1. **For Test Classes:**
   - Extract helper methods to utility classes
   - Split into multiple test classes by feature
   - Use nested test classes for organization

2. **For Implementation Classes:**
   - Identify single responsibility violations
   - Extract inner classes
   - Create strategy pattern for complex logic

**Example Refactoring (AbstractGrpcTest):**

```java
// Before: One large class with all tests

// After: Split into focused test classes
AbstractGrpcTest (base class)
├── GrpcStreamingTest
├── GrpcUnaryTest
├── GrpcErrorHandlingTest
└── GrpcMetadataTest
```

**Estimated Time:** 2 hours per file (10 hours total)  
**Priority:** LOW (functional code, not urgent)

---

## Phase 4: Long-term Strategic Improvements

### TODO: Java Version Migration Plan

**Current State:** Java 8 minimum support

**Migration Options:**

| Scenario | Timeline | Risk | Benefit |
|----------|----------|------|---------|
| Stay on Java 8 | Ongoing | LOW | Maximum compatibility |
| Migrate to Java 11 | 2026 Q2 | MEDIUM | Modern features, still LTS |
| Migrate to Java 17 | 2026 Q4 | MEDIUM | Latest LTS, better APIs |

**Recommendation:** Stay on Java 8 for javaagent, consider Java 11 for library instrumentation

**Action Items:**
1. Monitor Java 8 usage statistics
2. Survey user base
3. Document migration path
4. Plan feature flagging for modern APIs

**Timeline:** 12-18 months

---

### TODO: Build Performance Optimization

**Objective:** Reduce build time

**Current Baseline:** 
```bash
# Measure current build time
time ./gradlew clean build --no-build-cache
```

**Optimization Strategies:**

1. **Enable Gradle Configuration Cache:**
   ```bash
   ./gradlew build --configuration-cache
   ```

2. **Optimize Test Execution:**
   ```kotlin
   tasks.withType<Test> {
     maxParallelForks = Runtime.getRuntime().availableProcessors() / 2
     setForkEvery(100)
   }
   ```

3. **Remote Build Cache:**
   - Configure Gradle Enterprise properly
   - Ensure cache keys are stable
   - Monitor hit rates

4. **Profile Build:**
   ```bash
   ./gradlew build --profile --scan
   ```

**Target:** 20-30% reduction in build time

**Estimated Time:** 1 week of investigation + implementation

---

## Completed Improvements

### ✅ GitHub Actions Workflow Update

**Date:** October 8, 2025  
**PR:** #[number]  
**Changes:**
- Updated all GitHub Actions to latest versions
- Updated Java version to 21
- Replaced deprecated release actions
- Added better error handling

**Impact:** CI/CD reliability improved, security vulnerabilities in Actions removed

---

### ✅ Build System Documentation

**Date:** October 8, 2025  
**File:** `docs/architecture/BUILD_SYSTEM.md`  
**Content:** Comprehensive guide to build system, workarounds, and troubleshooting

**Impact:** Reduced onboarding time for new contributors, better understanding of build quirks

---

### ✅ Technical Debt Report

**Date:** October 8, 2025  
**File:** `TECHNICAL_DEBT_REPORT.md`  
**Content:** Comprehensive analysis of codebase health and improvement opportunities

**Impact:** Clear roadmap for improvements, prioritized action items

---

## Ongoing Maintenance

### Monthly Tasks
- [ ] Review Renovate PRs
- [ ] Check OWASP dependency scan results
- [ ] Update this action plan with progress

### Quarterly Tasks
- [ ] Review and update dependency versions
- [ ] Audit TODO comments and convert to issues
- [ ] Review build performance metrics
- [ ] Update technical debt report

### Annual Tasks
- [ ] Major dependency updates (e.g., Gradle, Kotlin)
- [ ] Java version strategy review
- [ ] Comprehensive code quality audit

---

## Success Metrics

### Build Health
- ✅ Zero deprecated GitHub Actions
- ⏳ Build time < 15 minutes (baseline TBD)
- ⏳ Test success rate > 99%
- ⏳ Build cache hit rate > 80%

### Code Quality
- ⏳ Code coverage > 70%
- ⏳ Zero HIGH/CRITICAL security vulnerabilities
- ⏳ Checkstyle violations < 100
- ⏳ Active TODO comments < 10

### Developer Experience
- ✅ Build system documented
- ⏳ Onboarding time < 1 hour
- ⏳ Build failures due to config < 5%
- ⏳ IntelliJ indexing time < 10 minutes

---

## Resources

### Documentation
- [TECHNICAL_DEBT_REPORT.md](./TECHNICAL_DEBT_REPORT.md)
- [BUILD_SYSTEM.md](./docs/architecture/BUILD_SYSTEM.md)
- [CONTRIBUTING.md](./CONTRIBUTING.md)

### Tools
- Gradle: https://gradle.org/
- Renovate: https://docs.renovatebot.com/
- OWASP Dependency Check: https://owasp.org/www-project-dependency-check/
- Jacoco: https://www.jacoco.org/

### External References
- [Gradle Best Practices](https://docs.gradle.org/current/userguide/performance.html)
- [GitHub Actions](https://docs.github.com/en/actions)
- [Java LTS Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)

---

**Last Updated:** October 8, 2025  
**Next Review:** October 22, 2025 (2 weeks)
