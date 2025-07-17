/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.coverage.execute;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.logging.Logger;

import org.pitest.coverage.CoverageReceiver;
import org.pitest.extension.common.TestUnitDecorator;
import org.pitest.testapi.Description;
import org.pitest.testapi.ResultCollector;
import org.pitest.testapi.TestUnit;
import org.pitest.util.Log;

public class CoverageDecorator extends TestUnitDecorator {

  private static final Logger    LOG     = Log.getLogger();

  private final CoverageReceiver invokeQueue;
  private final ThreadMXBean     threads = ManagementFactory.getThreadMXBean();

  protected CoverageDecorator(final CoverageReceiver queue, final TestUnit child) {
    super(child);
    this.invokeQueue = queue;
  }

  @Override
  public void execute(final ResultCollector rc) {
    LOG.fine(() -> "Gathering coverage for test " + child().getDescription());

    final int threadsBeforeTest = this.threads.getThreadCount();

    final long t0 = System.nanoTime();
    final ExceptionCapturingResultCollector wrappedCollector = new ExceptionCapturingResultCollector(rc);
    this.child().execute(wrappedCollector);

    // Calculate execution time in milliseconds with full precision
    final long executionTimeNanos = System.nanoTime() - t0;
    final double executionTime = executionTimeNanos / 1_000_000.0;

    final int threadsAfterTest = this.threads.getThreadCount();
    if (threadsAfterTest > threadsBeforeTest) {
      LOG.warning("More threads at end of test (" + threadsAfterTest + ") "
          + child().getDescription().getName() + " than start. ("
          + threadsBeforeTest + ")");
    }

    // Get exception details from the collector
    boolean testPassed = !wrappedCollector.shouldExit();
    Throwable exception = wrappedCollector.getException();
    String exceptionType = "None";
    String exceptionMessage = "None";
    String stackTrace = "None";
    
    if (!testPassed && exception != null) {
        exceptionType = exception.getClass().getSimpleName();
        exceptionMessage = exception.getMessage() != null ? exception.getMessage() : "";
        stackTrace = getStackTraceString(exception);
    }

    // Send result with exception information
    this.invokeQueue.recordTestOutcome(child().getDescription(),
        testPassed, executionTime, exceptionType, exceptionMessage, stackTrace);
  }

  private String getStackTraceString(Throwable throwable) {
    if (throwable == null) {
      return "";
    }
    
    java.io.StringWriter sw = new java.io.StringWriter();
    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
    throwable.printStackTrace(pw);
    pw.close();
    
    // Return full stack trace without truncation
    return sw.toString();
  }

  /**
   * Result collector that captures exception details for coverage reporting
   */
  private static class ExceptionCapturingResultCollector implements ResultCollector {
    private final ResultCollector child;
    private final java.util.concurrent.atomic.AtomicBoolean hadFailure = new java.util.concurrent.atomic.AtomicBoolean(false);
    private volatile Throwable exception;

    ExceptionCapturingResultCollector(final ResultCollector child) {
      this.child = child;
    }

    @Override
    public void notifySkipped(final Description description) {
      this.child.notifySkipped(description);
    }

    @Override
    public void notifyStart(final Description description) {
      this.child.notifyStart(description);
    }

    @Override
    public boolean shouldExit() {
      return this.hadFailure.get();
    }

    @Override
    public void notifyEnd(final Description description, final Throwable t) {
      this.child.notifyEnd(description, t);
      if (t != null) {
        this.hadFailure.set(true);
        this.exception = t;
      }
    }

    @Override
    public void notifyEnd(final Description description) {
      this.child.notifyEnd(description);
    }

    public Throwable getException() {
      return this.exception;
    }
  }

}
