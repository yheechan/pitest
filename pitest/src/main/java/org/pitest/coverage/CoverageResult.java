package org.pitest.coverage;

import java.util.Collection;

import org.pitest.testapi.Description;

public class CoverageResult {

  private final Description               testUnitDescription;
  private final double                    executionTime;
  private final Collection<BlockLocation> visitedBlocks;
  private final boolean                   greenSuite;
  private final String                    exceptionType;
  private final String                    exceptionMessage;
  private final String                    stackTrace;

  public CoverageResult(final Description testUnitDescription,
      final double executionTime, final boolean greenSuite,
      final Collection<BlockLocation> visitedBlocks) {
    this(testUnitDescription, executionTime, greenSuite, visitedBlocks, "None", "None", "None");
  }

  public CoverageResult(final Description testUnitDescription,
      final double executionTime, final boolean greenSuite,
      final Collection<BlockLocation> visitedBlocks,
      final String exceptionType, final String exceptionMessage, final String stackTrace) {
    this.testUnitDescription = testUnitDescription;
    this.executionTime = executionTime;
    this.visitedBlocks = visitedBlocks;
    this.greenSuite = greenSuite;
    this.exceptionType = exceptionType != null ? exceptionType : "None";
    this.exceptionMessage = exceptionMessage != null ? exceptionMessage : "None";
    this.stackTrace = stackTrace != null ? stackTrace : "None";
  }

  public Description getTestUnitDescription() {
    return this.testUnitDescription;
  }

  public double getExecutionTime() {
    return this.executionTime;
  }

  public Collection<BlockLocation> getCoverage() {
    return this.visitedBlocks;
  }

  public boolean isGreenTest() {
    return this.greenSuite;
  }

  public String getExceptionType() {
    return this.exceptionType;
  }

  public String getExceptionMessage() {
    return this.exceptionMessage;
  }

  public String getStackTrace() {
    return this.stackTrace;
  }

  public int getNumberOfCoveredBlocks() {
    return this.visitedBlocks.size();
  }

  @Override
  public String toString() {
    return "CoverageResult [testUnitDescription=" + this.testUnitDescription
        + ", executionTime=" + this.executionTime + ", coverage="
        + this.visitedBlocks + ", greenSuite=" + this.greenSuite 
        + ", exceptionType=" + this.exceptionType
        + ", exceptionMessage=" + this.exceptionMessage
        + ", stackTrace=" + (this.stackTrace.length() > 100 ? this.stackTrace.substring(0, 100) + "..." : this.stackTrace) + "]";
  }

}
