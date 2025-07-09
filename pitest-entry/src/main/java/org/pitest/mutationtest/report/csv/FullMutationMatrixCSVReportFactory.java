/*
 * CSV factory for detailed mutation-test matrix data
 * For fault localization research on Defects4J
 */
package org.pitest.mutationtest.report.csv;

import java.util.Properties;

import org.pitest.mutationtest.ListenerArguments;
import org.pitest.mutationtest.MutationResultListener;
import org.pitest.mutationtest.MutationResultListenerFactory;
import org.pitest.plugin.Feature;

public class FullMutationMatrixCSVReportFactory implements MutationResultListenerFactory {

  public static final Feature FULL_MATRIX_RESEARCH_MODE = Feature.named("FULL_MATRIX_RESEARCH")
      .withOnByDefault(false);

  @Override
  public MutationResultListener getListener(Properties props,
      final ListenerArguments args) {
    // Create the listener when the feature is activated (by setting the research mode flag)
    try {
      return new FullMutationMatrixCSVReportListener(args.getOutputStrategy());
    } catch (Exception e) {
      throw new RuntimeException("Failed to create full mutation matrix CSV listener", e);
    }
  }

  @Override
  public String name() {
    return "FULL_MATRIX_CSV";
  }

  @Override
  public String description() {
    return "Full mutation matrix CSV report plugin for research mode";
  }

  @Override
  public Feature provides() {
    return FULL_MATRIX_RESEARCH_MODE;
  }

}
