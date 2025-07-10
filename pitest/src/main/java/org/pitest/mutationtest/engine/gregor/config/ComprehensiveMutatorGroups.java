package org.pitest.mutationtest.engine.gregor.config;

import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.ArithmeticOperatorReplacementMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.ArithmeticOperatorDeletionMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.UnaryOperatorInsertionMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.BitwiseOperatorMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.NegationMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.ConstantReplacementMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.RelationalOperatorReplacementMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.NakedReceiverMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.RemoveIncrementsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.ArgumentPropagationMutator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Groups for comprehensive mutation operators based on mutation testing literature.
 * These provide complete coverage for each operator family.
 */
public class ComprehensiveMutatorGroups implements MutatorGroup {
    
    @Override
    public void register(Map<String, List<MethodMutatorFactory>> mutators) {
        
        // Register individual AOR sub-mutators
        mutators.put("AOR_1", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_1));
        mutators.put("AOR_2", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_2));
        mutators.put("AOR_3", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_3));
        mutators.put("AOR_4", Arrays.asList(ArithmeticOperatorReplacementMutator.AOR_4));
        
        // Register individual AOD sub-mutators
        mutators.put("AOD_1", Arrays.asList(ArithmeticOperatorDeletionMutator.AOD_1));
        mutators.put("AOD_2", Arrays.asList(ArithmeticOperatorDeletionMutator.AOD_2));
        
        // Register individual UOI sub-mutators
        mutators.put("UOI_1", Arrays.asList(UnaryOperatorInsertionMutator.UOI_1));
        mutators.put("UOI_2", Arrays.asList(UnaryOperatorInsertionMutator.UOI_2));
        mutators.put("UOI_3", Arrays.asList(UnaryOperatorInsertionMutator.UOI_3));
        mutators.put("UOI_4", Arrays.asList(UnaryOperatorInsertionMutator.UOI_4));
        
        // Register individual CRCR sub-mutators
        mutators.put("CRCR_1", Arrays.asList(ConstantReplacementMutator.CRCR_1));
        mutators.put("CRCR_2", Arrays.asList(ConstantReplacementMutator.CRCR_2));
        mutators.put("CRCR_3", Arrays.asList(ConstantReplacementMutator.CRCR_3));
        mutators.put("CRCR_4", Arrays.asList(ConstantReplacementMutator.CRCR_4));
        mutators.put("CRCR_5", Arrays.asList(ConstantReplacementMutator.CRCR_5));
        mutators.put("CRCR_6", Arrays.asList(ConstantReplacementMutator.CRCR_6));
        
        // Register individual OBBN sub-mutators
        mutators.put("OBBN_1", Arrays.asList(BitwiseOperatorMutator.OBBN_1));
        mutators.put("OBBN_2", Arrays.asList(BitwiseOperatorMutator.OBBN_2));
        mutators.put("OBBN_3", Arrays.asList(BitwiseOperatorMutator.OBBN_3));
        
        // Register individual ROR sub-mutators
        mutators.put("ROR_1", Arrays.asList(RelationalOperatorReplacementMutator.ROR_1));
        mutators.put("ROR_2", Arrays.asList(RelationalOperatorReplacementMutator.ROR_2));
        mutators.put("ROR_3", Arrays.asList(RelationalOperatorReplacementMutator.ROR_3));
        mutators.put("ROR_4", Arrays.asList(RelationalOperatorReplacementMutator.ROR_4));
        mutators.put("ROR_5", Arrays.asList(RelationalOperatorReplacementMutator.ROR_5));
        
        // Register ABS mutator
        mutators.put("ABS", Arrays.asList(NegationMutator.ABS));
        
        // Register other experimental mutators (single-value enums)
        mutators.put("NAKED_RECEIVER", Arrays.asList(NakedReceiverMutator.EXPERIMENTAL_NAKED_RECEIVER));
        mutators.put("REMOVE_INCREMENTS", Arrays.asList(RemoveIncrementsMutator.REMOVE_INCREMENTS));
        mutators.put("ARGUMENT_PROPAGATION", Arrays.asList(ArgumentPropagationMutator.EXPERIMENTAL_ARGUMENT_PROPAGATION));
        
        // Register comprehensive groups
        mutators.put("AOR_ALL", Arrays.asList(
            ArithmeticOperatorReplacementMutator.AOR_1,
            ArithmeticOperatorReplacementMutator.AOR_2,
            ArithmeticOperatorReplacementMutator.AOR_3,
            ArithmeticOperatorReplacementMutator.AOR_4
        ));
        
        mutators.put("AOD_ALL", Arrays.asList(
            ArithmeticOperatorDeletionMutator.AOD_1,
            ArithmeticOperatorDeletionMutator.AOD_2
        ));
        
        mutators.put("UOI_ALL", Arrays.asList(
            UnaryOperatorInsertionMutator.UOI_1,
            UnaryOperatorInsertionMutator.UOI_2,
            UnaryOperatorInsertionMutator.UOI_3,
            UnaryOperatorInsertionMutator.UOI_4
        ));
        
        mutators.put("CRCR_ALL", Arrays.asList(
            ConstantReplacementMutator.CRCR_1,
            ConstantReplacementMutator.CRCR_2,
            ConstantReplacementMutator.CRCR_3,
            ConstantReplacementMutator.CRCR_4,
            ConstantReplacementMutator.CRCR_5,
            ConstantReplacementMutator.CRCR_6
        ));
        
        mutators.put("OBBN_ALL", Arrays.asList(
            BitwiseOperatorMutator.OBBN_1,
            BitwiseOperatorMutator.OBBN_2,
            BitwiseOperatorMutator.OBBN_3
        ));
        
        mutators.put("ROR_ALL", Arrays.asList(
            RelationalOperatorReplacementMutator.ROR_1,
            RelationalOperatorReplacementMutator.ROR_2,
            RelationalOperatorReplacementMutator.ROR_3,
            RelationalOperatorReplacementMutator.ROR_4,
            RelationalOperatorReplacementMutator.ROR_5
        ));
        
        // Comprehensive set of all experimental mutators
        List<MethodMutatorFactory> allComprehensive = new ArrayList<>();
        allComprehensive.addAll(Arrays.asList(
            // AOR family
            ArithmeticOperatorReplacementMutator.AOR_1,
            ArithmeticOperatorReplacementMutator.AOR_2,
            ArithmeticOperatorReplacementMutator.AOR_3,
            ArithmeticOperatorReplacementMutator.AOR_4,
            // AOD family
            ArithmeticOperatorDeletionMutator.AOD_1,
            ArithmeticOperatorDeletionMutator.AOD_2,
            // UOI family
            UnaryOperatorInsertionMutator.UOI_1,
            UnaryOperatorInsertionMutator.UOI_2,
            UnaryOperatorInsertionMutator.UOI_3,
            UnaryOperatorInsertionMutator.UOI_4,
            // CRCR family
            ConstantReplacementMutator.CRCR_1,
            ConstantReplacementMutator.CRCR_2,
            ConstantReplacementMutator.CRCR_3,
            ConstantReplacementMutator.CRCR_4,
            ConstantReplacementMutator.CRCR_5,
            ConstantReplacementMutator.CRCR_6,
            // OBBN family
            BitwiseOperatorMutator.OBBN_1,
            BitwiseOperatorMutator.OBBN_2,
            BitwiseOperatorMutator.OBBN_3,
            // ROR family
            RelationalOperatorReplacementMutator.ROR_1,
            RelationalOperatorReplacementMutator.ROR_2,
            RelationalOperatorReplacementMutator.ROR_3,
            RelationalOperatorReplacementMutator.ROR_4,
            RelationalOperatorReplacementMutator.ROR_5,
            // Single mutators
            NegationMutator.ABS,
            NakedReceiverMutator.EXPERIMENTAL_NAKED_RECEIVER,
            RemoveIncrementsMutator.REMOVE_INCREMENTS,
            ArgumentPropagationMutator.EXPERIMENTAL_ARGUMENT_PROPAGATION
        ));
        
        mutators.put("COMPREHENSIVE", allComprehensive);
    }
}
