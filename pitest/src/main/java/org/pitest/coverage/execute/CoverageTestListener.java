package org.pitest.coverage.execute;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.pitest.testapi.Description;
import org.pitest.testapi.TestListener;
import org.pitest.testapi.TestResult;
import org.pitest.util.Log;

/**
 * Test listener for coverage calculation phase.
 * Exception details are now captured and transmitted via the CoverageResult protocol.
 */
public class CoverageTestListener implements TestListener {
  private static final Logger LOG = Log.getLogger();

  @Override
  public void onRunStart() {
    // Run start logic if needed
  }

  @Override
  public void onTestStart(final Description d) {
    // Test start logic if needed
  }

  @Override
  public void onTestFailure(final TestResult tr) {
    // Log the error as before
    LOG.log(Level.SEVERE, tr.getDescription().toString(), tr.getThrowable());
  }

  @Override
  public void onTestSkipped(final TestResult tr) {
    // Test skipped logic if needed
  }

  @Override
  public void onTestSuccess(final TestResult tr) {
    // Test success logic if needed
  }

  @Override
  public void onRunEnd() {
    // Run end logic if needed
  }
}
