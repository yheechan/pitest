package org.pitest.mutationtest.build.intercept.research;

import org.pitest.bytecode.analysis.ClassTree;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.config.ReportOptions;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.execute.BaselineResultsHolder;
import org.pitest.util.Log;

import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Filters mutations to only those on lines covered by failing tests.
 * This is used in fullMatrixResearchMode for fault localization research.
 * Uses baseline results saved during initial coverage analysis.
 */
public class FailingTestCoverageFilter implements MutationInterceptor {
    private static final Logger LOG = Log.getLogger();
    private final ReportOptions reportOptions;
    private Set<String> failingTestLines; // Stores "class:line" strings
    
    public FailingTestCoverageFilter(ReportOptions reportOptions) {
        this.reportOptions = reportOptions;
        this.failingTestLines = new HashSet<>();
        
        LOG.info("FailingTestCoverageFilter - creating filter for research mode");
    }

    @Override
    public InterceptorType type() {
        return InterceptorType.FILTER;
    }

    @Override
    public void begin(ClassTree clazz) {
        // Only load baseline results when we need them
        if (reportOptions.isFullMatrixResearchMode()) {
            loadFailingTestLinesFromBaseline();
        }
    }

    @Override
    public Collection<MutationDetails> intercept(Collection<MutationDetails> mutations, Mutater mutater) {
        if (!reportOptions.isFullMatrixResearchMode()) {
            // In normal mode, don't filter anything
            return mutations;
        }

        LOG.info("FailingTestCoverageFilter - filtering mutations for class " 
                          + (mutations.isEmpty() ? "unknown" : mutations.iterator().next().getClassName()) 
                          + " based on " + failingTestLines.size() + " failing test lines");

        return mutations.stream()
            .filter(this::isOnFailingTestLine)
            .collect(Collectors.toList());
    }

    private boolean isOnFailingTestLine(MutationDetails mutation) {
        int lineNumber = mutation.getLineNumber();
        String className = mutation.getClassName().asJavaName();
        
        // Create class:line key to check against failing test lines
        String classLine = className + ":" + lineNumber;
        boolean isOnFailingLine = failingTestLines.contains(classLine);

        LOG.fine("Mutation " + mutation.getId() + " at line " + lineNumber 
                          + " in class " + className + " (" + classLine + ") - "
                          + (isOnFailingLine ? "INCLUDED" : "EXCLUDED"));

        return isOnFailingLine;
    }

    /**
     * Loads failing test lines from baseline results saved during coverage analysis.
     */
    private void loadFailingTestLinesFromBaseline() {
        try {
            // Get baseline results saved during MutationCoverage analysis
            Set<String> baselineFailingTestLines = BaselineResultsHolder.getFailingTestLines();
            
            if (baselineFailingTestLines != null && !baselineFailingTestLines.isEmpty()) {
                failingTestLines.clear();
                failingTestLines.addAll(baselineFailingTestLines);
                LOG.info("Loaded " + failingTestLines.size() + " failing test lines from baseline results");
                LOG.fine("Failing test lines: " + failingTestLines);
            } else {
                LOG.warning("No failing test lines found in baseline results");
                failingTestLines.clear();
            }
        } catch (Exception e) {
            LOG.warning("Failed to load failing test lines from baseline: " + e.getMessage());
            e.printStackTrace();
            failingTestLines.clear();
        }
    }

    @Override
    public void end() {
        // Clean up if needed
        failingTestLines.clear();
    }
}
