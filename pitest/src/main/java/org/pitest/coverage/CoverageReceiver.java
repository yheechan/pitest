package org.pitest.coverage;

import org.pitest.testapi.Description;

import sun.pitest.InvokeReceiver;

public interface CoverageReceiver extends InvokeReceiver {

  void recordTestOutcome(Description description, boolean wasGreen,
      double executionTime);

  default void recordTestOutcome(Description description, boolean wasGreen,
      double executionTime, String exceptionType, String exceptionMessage, String stackTrace) {
    // Default implementation for backward compatibility
    recordTestOutcome(description, wasGreen, executionTime);
  }

}