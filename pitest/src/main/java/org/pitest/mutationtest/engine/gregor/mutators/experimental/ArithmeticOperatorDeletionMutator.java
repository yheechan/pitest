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
package org.pitest.mutationtest.engine.gregor.mutators.experimental;

import java.util.HashMap;
import java.util.Map;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.pitest.mutationtest.engine.gregor.AbstractInsnMutator;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.mutationtest.engine.gregor.ZeroOperandMutation;

/**
 * Arithmetic Operator Deletion Mutator (AOD)
 * 
 * This mutator replaces an arithmetic operation with one of its operands.
 * AOD_1 replaces with the first operand, AOD_2 replaces with the second operand.
 */
public enum ArithmeticOperatorDeletionMutator implements MethodMutatorFactory {

  AOD_1("AOD_1"),
  AOD_2("AOD_2");

  private final String name;

  ArithmeticOperatorDeletionMutator(String name) {
    this.name = name;
  }

  @Override
  public MethodVisitor create(final MutationContext context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new ArithmeticOperatorDeletionMethodVisitor(this, methodInfo, context, methodVisitor);
  }

  @Override
  public String getGloballyUniqueId() {
    return this.getClass().getName() + "_" + name;
  }

  @Override
  public String getName() {
    return name;
  }

}

class ArithmeticOperatorDeletionMethodVisitor extends AbstractInsnMutator {

  private final ArithmeticOperatorDeletionMutator mutatorType;

  ArithmeticOperatorDeletionMethodVisitor(final ArithmeticOperatorDeletionMutator mutatorType,
      final MethodInfo methodInfo, final MutationContext context,
      final MethodVisitor writer) {
    super(mutatorType, methodInfo, context, writer);
    this.mutatorType = mutatorType;
  }

  private static final Map<ArithmeticOperatorDeletionMutator, Map<Integer, ZeroOperandMutation>> MUTATIONS = new HashMap<>();

  static {
    // AOD_1: Replace arithmetic operation with first operand (pop second operand)
    Map<Integer, ZeroOperandMutation> aod1 = new HashMap<>();
    aod1.put(Opcodes.IADD, new AODMutation(Opcodes.POP, "AOD_1: Replaced integer addition with first operand"));
    aod1.put(Opcodes.ISUB, new AODMutation(Opcodes.POP, "AOD_1: Replaced integer subtraction with first operand"));
    aod1.put(Opcodes.IMUL, new AODMutation(Opcodes.POP, "AOD_1: Replaced integer multiplication with first operand"));
    aod1.put(Opcodes.IDIV, new AODMutation(Opcodes.POP, "AOD_1: Replaced integer division with first operand"));
    aod1.put(Opcodes.IREM, new AODMutation(Opcodes.POP, "AOD_1: Replaced integer modulus with first operand"));
    
    aod1.put(Opcodes.LADD, new AODMutation(Opcodes.POP2, "AOD_1: Replaced long addition with first operand"));
    aod1.put(Opcodes.LSUB, new AODMutation(Opcodes.POP2, "AOD_1: Replaced long subtraction with first operand"));
    aod1.put(Opcodes.LMUL, new AODMutation(Opcodes.POP2, "AOD_1: Replaced long multiplication with first operand"));
    aod1.put(Opcodes.LDIV, new AODMutation(Opcodes.POP2, "AOD_1: Replaced long division with first operand"));
    aod1.put(Opcodes.LREM, new AODMutation(Opcodes.POP2, "AOD_1: Replaced long modulus with first operand"));
    
    aod1.put(Opcodes.FADD, new AODMutation(Opcodes.POP, "AOD_1: Replaced float addition with first operand"));
    aod1.put(Opcodes.FSUB, new AODMutation(Opcodes.POP, "AOD_1: Replaced float subtraction with first operand"));
    aod1.put(Opcodes.FMUL, new AODMutation(Opcodes.POP, "AOD_1: Replaced float multiplication with first operand"));
    aod1.put(Opcodes.FDIV, new AODMutation(Opcodes.POP, "AOD_1: Replaced float division with first operand"));
    aod1.put(Opcodes.FREM, new AODMutation(Opcodes.POP, "AOD_1: Replaced float modulus with first operand"));
    
    aod1.put(Opcodes.DADD, new AODMutation(Opcodes.POP2, "AOD_1: Replaced double addition with first operand"));
    aod1.put(Opcodes.DSUB, new AODMutation(Opcodes.POP2, "AOD_1: Replaced double subtraction with first operand"));
    aod1.put(Opcodes.DMUL, new AODMutation(Opcodes.POP2, "AOD_1: Replaced double multiplication with first operand"));
    aod1.put(Opcodes.DDIV, new AODMutation(Opcodes.POP2, "AOD_1: Replaced double division with first operand"));
    aod1.put(Opcodes.DREM, new AODMutation(Opcodes.POP2, "AOD_1: Replaced double modulus with first operand"));
    MUTATIONS.put(ArithmeticOperatorDeletionMutator.AOD_1, aod1);

    // AOD_2: Replace arithmetic operation with second operand (swap and pop first operand)
    Map<Integer, ZeroOperandMutation> aod2 = new HashMap<>();
    aod2.put(Opcodes.IADD, new AODSwapMutation(false, "AOD_2: Replaced integer addition with second operand"));
    aod2.put(Opcodes.ISUB, new AODSwapMutation(false, "AOD_2: Replaced integer subtraction with second operand"));
    aod2.put(Opcodes.IMUL, new AODSwapMutation(false, "AOD_2: Replaced integer multiplication with second operand"));
    aod2.put(Opcodes.IDIV, new AODSwapMutation(false, "AOD_2: Replaced integer division with second operand"));
    aod2.put(Opcodes.IREM, new AODSwapMutation(false, "AOD_2: Replaced integer modulus with second operand"));
    
    aod2.put(Opcodes.LADD, new AODSwapMutation(true, "AOD_2: Replaced long addition with second operand"));
    aod2.put(Opcodes.LSUB, new AODSwapMutation(true, "AOD_2: Replaced long subtraction with second operand"));
    aod2.put(Opcodes.LMUL, new AODSwapMutation(true, "AOD_2: Replaced long multiplication with second operand"));
    aod2.put(Opcodes.LDIV, new AODSwapMutation(true, "AOD_2: Replaced long division with second operand"));
    aod2.put(Opcodes.LREM, new AODSwapMutation(true, "AOD_2: Replaced long modulus with second operand"));
    
    aod2.put(Opcodes.FADD, new AODSwapMutation(false, "AOD_2: Replaced float addition with second operand"));
    aod2.put(Opcodes.FSUB, new AODSwapMutation(false, "AOD_2: Replaced float subtraction with second operand"));
    aod2.put(Opcodes.FMUL, new AODSwapMutation(false, "AOD_2: Replaced float multiplication with second operand"));
    aod2.put(Opcodes.FDIV, new AODSwapMutation(false, "AOD_2: Replaced float division with second operand"));
    aod2.put(Opcodes.FREM, new AODSwapMutation(false, "AOD_2: Replaced float modulus with second operand"));
    
    aod2.put(Opcodes.DADD, new AODSwapMutation(true, "AOD_2: Replaced double addition with second operand"));
    aod2.put(Opcodes.DSUB, new AODSwapMutation(true, "AOD_2: Replaced double subtraction with second operand"));
    aod2.put(Opcodes.DMUL, new AODSwapMutation(true, "AOD_2: Replaced double multiplication with second operand"));
    aod2.put(Opcodes.DDIV, new AODSwapMutation(true, "AOD_2: Replaced double division with second operand"));
    aod2.put(Opcodes.DREM, new AODSwapMutation(true, "AOD_2: Replaced double modulus with second operand"));
    MUTATIONS.put(ArithmeticOperatorDeletionMutator.AOD_2, aod2);
  }

  @Override
  protected Map<Integer, ZeroOperandMutation> getMutations() {
    return MUTATIONS.get(mutatorType);
  }

  private static class AODMutation implements ZeroOperandMutation {
    private final int popOpcode;
    private final String description;

    AODMutation(int popOpcode, String description) {
      this.popOpcode = popOpcode;
      this.description = description;
    }

    @Override
    public void apply(int opcode, MethodVisitor mv) {
      mv.visitInsn(popOpcode);  // Just pop the second operand, leaving first operand on stack
    }

    @Override
    public String describe(int opcode, MethodInfo methodInfo) {
      return description;
    }
  }

  private static class AODSwapMutation implements ZeroOperandMutation {
    private final boolean isCategory2;
    private final String description;

    AODSwapMutation(boolean isCategory2, String description) {
      this.isCategory2 = isCategory2;
      this.description = description;
    }

    @Override
    public void apply(int opcode, MethodVisitor mv) {
      if (isCategory2) {
        // For long/double: [first][second] -> [second]
        mv.visitInsn(Opcodes.DUP2_X2);  // [first][second][first]
        mv.visitInsn(Opcodes.POP2);     // [first][second]
        mv.visitInsn(Opcodes.POP2);     // [second]
      } else {
        // For int/float: [first][second] -> [second]
        mv.visitInsn(Opcodes.SWAP);     // [second][first]
        mv.visitInsn(Opcodes.POP);      // [second]
      }
    }

    @Override
    public String describe(int opcode, MethodInfo methodInfo) {
      return description;
    }
  }

}
