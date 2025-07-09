package org.pitest.mutationtest.build.intercept.research;

import org.pitest.mutationtest.build.InterceptorParameters;
import org.pitest.mutationtest.build.MutationInterceptor;
import org.pitest.mutationtest.build.MutationInterceptorFactory;
import org.pitest.plugin.Feature;

/**
 * Factory for creating FailingTestCoverageFilter interceptors.
 * Only active in fullMatrixResearchMode.
 */
public class FailingTestCoverageFilterFactory implements MutationInterceptorFactory {

    @Override
    public String description() {
        return "Filters mutations to only those on lines executed by failing tests (research mode only)";
    }

    @Override
    public MutationInterceptor createInterceptor(InterceptorParameters params) {
        return new FailingTestCoverageFilter(params.data());
    }

    @Override
    public Feature provides() {
        return Feature.named("ftcovfilter")
                .withDescription(description())
                .withOnByDefault(true);
    }
}
