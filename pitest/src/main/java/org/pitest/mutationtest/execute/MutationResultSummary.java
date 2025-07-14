/*
 * Summary information for a mutation result
 */
package org.pitest.mutationtest.execute;

/**
 * Summary data for a mutation result to be used in CSV reporting
 */
public class MutationResultSummary {
    
    private final long mutationId;
    private final String mutantDescription;
    private final int numTestsExecuted;
    private final String overallStatus;
    
    public MutationResultSummary(long mutationId, String mutantDescription, 
                               int numTestsExecuted, String overallStatus) {
        this.mutationId = mutationId;
        this.mutantDescription = mutantDescription;
        this.numTestsExecuted = numTestsExecuted;
        this.overallStatus = overallStatus;
    }
    
    public long getMutationId() {
        return mutationId;
    }
    
    public String getMutantDescription() {
        return mutantDescription;
    }
    
    public int getNumTestsExecuted() {
        return numTestsExecuted;
    }
    
    public String getOverallStatus() {
        return overallStatus;
    }
    
    @Override
    public String toString() {
        return String.format("MutationResultSummary{id=%d, description='%s', numTests=%d, status='%s'}", 
                           mutationId, mutantDescription, numTestsExecuted, overallStatus);
    }
}
