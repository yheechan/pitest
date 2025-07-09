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
import org.pitest.mutationtest.engine.gregor.InsnSubstitution;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;
import org.pitest.mutationtest.engine.gregor.ZeroOperandMutation;

/**
 * Arithmetic Operator Replacement Mutator (AOR)
 * 
 * This mutator replaces binary arithmetic operations with other arithmetic operations
 * according to a comprehensive replacement table, providing more mutation coverage
 * than the basic Math mutator.
 */
public enum ArithmeticOperatorReplacementMutator implements MethodMutatorFactory {

  AOR_1("AOR_1"),
  AOR_2("AOR_2"),
  AOR_3("AOR_3"),
  AOR_4("AOR_4");

  private final String name;

  ArithmeticOperatorReplacementMutator(String name) {
    this.name = name;
  }

  @Override
  public MethodVisitor create(final MutationContext context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new ArithmeticOperatorReplacementMethodVisitor(this, methodInfo, context, methodVisitor);
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

class ArithmeticOperatorReplacementMethodVisitor extends AbstractInsnMutator {

  private final ArithmeticOperatorReplacementMutator mutatorType;

  ArithmeticOperatorReplacementMethodVisitor(final ArithmeticOperatorReplacementMutator mutatorType,
      final MethodInfo methodInfo, final MutationContext context,
      final MethodVisitor writer) {
    super(mutatorType, methodInfo, context, writer);
    this.mutatorType = mutatorType;
  }

  private static final Map<ArithmeticOperatorReplacementMutator, Map<Integer, ZeroOperandMutation>> MUTATIONS = new HashMap<>();

  static {
    /*
     * Comprehensive AOR implementation based on the complete replacement table:
     * Original |  AOR_1  |  AOR_2  |  AOR_3  |  AOR_4
     *    +     |    -    |    *    |    /    |    %
     *    -     |    +    |    *    |    /    |    %
     *    *     |    /    |    %    |    +    |    -
     *    /     |    *    |    %    |    +    |    -
     *    %     |    *    |    /    |    +    |    -
     */

    // AOR_1: + -> -, - -> +, * -> /, / -> *, % -> *
    Map<Integer, ZeroOperandMutation> aor1 = new HashMap<>();
    // Addition to subtraction
    aor1.put(Opcodes.IADD, new InsnSubstitution(Opcodes.ISUB, "AOR_1: Replaced integer addition with subtraction"));
    aor1.put(Opcodes.LADD, new InsnSubstitution(Opcodes.LSUB, "AOR_1: Replaced long addition with subtraction"));
    aor1.put(Opcodes.FADD, new InsnSubstitution(Opcodes.FSUB, "AOR_1: Replaced float addition with subtraction"));
    aor1.put(Opcodes.DADD, new InsnSubstitution(Opcodes.DSUB, "AOR_1: Replaced double addition with subtraction"));
    // Subtraction to addition
    aor1.put(Opcodes.ISUB, new InsnSubstitution(Opcodes.IADD, "AOR_1: Replaced integer subtraction with addition"));
    aor1.put(Opcodes.LSUB, new InsnSubstitution(Opcodes.LADD, "AOR_1: Replaced long subtraction with addition"));
    aor1.put(Opcodes.FSUB, new InsnSubstitution(Opcodes.FADD, "AOR_1: Replaced float subtraction with addition"));
    aor1.put(Opcodes.DSUB, new InsnSubstitution(Opcodes.DADD, "AOR_1: Replaced double subtraction with addition"));
    // Multiplication to division
    aor1.put(Opcodes.IMUL, new InsnSubstitution(Opcodes.IDIV, "AOR_1: Replaced integer multiplication with division"));
    aor1.put(Opcodes.LMUL, new InsnSubstitution(Opcodes.LDIV, "AOR_1: Replaced long multiplication with division"));
    aor1.put(Opcodes.FMUL, new InsnSubstitution(Opcodes.FDIV, "AOR_1: Replaced float multiplication with division"));
    aor1.put(Opcodes.DMUL, new InsnSubstitution(Opcodes.DDIV, "AOR_1: Replaced double multiplication with division"));
    // Division to multiplication
    aor1.put(Opcodes.IDIV, new InsnSubstitution(Opcodes.IMUL, "AOR_1: Replaced integer division with multiplication"));
    aor1.put(Opcodes.LDIV, new InsnSubstitution(Opcodes.LMUL, "AOR_1: Replaced long division with multiplication"));
    aor1.put(Opcodes.FDIV, new InsnSubstitution(Opcodes.FMUL, "AOR_1: Replaced float division with multiplication"));
    aor1.put(Opcodes.DDIV, new InsnSubstitution(Opcodes.DMUL, "AOR_1: Replaced double division with multiplication"));
    // Modulus to multiplication
    aor1.put(Opcodes.IREM, new InsnSubstitution(Opcodes.IMUL, "AOR_1: Replaced integer modulus with multiplication"));
    aor1.put(Opcodes.LREM, new InsnSubstitution(Opcodes.LMUL, "AOR_1: Replaced long modulus with multiplication"));
    aor1.put(Opcodes.FREM, new InsnSubstitution(Opcodes.FMUL, "AOR_1: Replaced float modulus with multiplication"));
    aor1.put(Opcodes.DREM, new InsnSubstitution(Opcodes.DMUL, "AOR_1: Replaced double modulus with multiplication"));
    MUTATIONS.put(ArithmeticOperatorReplacementMutator.AOR_1, aor1);

    // AOR_2: + -> *, - -> *, * -> %, / -> %, % -> /
    Map<Integer, ZeroOperandMutation> aor2 = new HashMap<>();
    // Addition to multiplication
    aor2.put(Opcodes.IADD, new InsnSubstitution(Opcodes.IMUL, "AOR_2: Replaced integer addition with multiplication"));
    aor2.put(Opcodes.LADD, new InsnSubstitution(Opcodes.LMUL, "AOR_2: Replaced long addition with multiplication"));
    aor2.put(Opcodes.FADD, new InsnSubstitution(Opcodes.FMUL, "AOR_2: Replaced float addition with multiplication"));
    aor2.put(Opcodes.DADD, new InsnSubstitution(Opcodes.DMUL, "AOR_2: Replaced double addition with multiplication"));
    // Subtraction to multiplication
    aor2.put(Opcodes.ISUB, new InsnSubstitution(Opcodes.IMUL, "AOR_2: Replaced integer subtraction with multiplication"));
    aor2.put(Opcodes.LSUB, new InsnSubstitution(Opcodes.LMUL, "AOR_2: Replaced long subtraction with multiplication"));
    aor2.put(Opcodes.FSUB, new InsnSubstitution(Opcodes.FMUL, "AOR_2: Replaced float subtraction with multiplication"));
    aor2.put(Opcodes.DSUB, new InsnSubstitution(Opcodes.DMUL, "AOR_2: Replaced double subtraction with multiplication"));
    // Multiplication to modulus
    aor2.put(Opcodes.IMUL, new InsnSubstitution(Opcodes.IREM, "AOR_2: Replaced integer multiplication with modulus"));
    aor2.put(Opcodes.LMUL, new InsnSubstitution(Opcodes.LREM, "AOR_2: Replaced long multiplication with modulus"));
    aor2.put(Opcodes.FMUL, new InsnSubstitution(Opcodes.FREM, "AOR_2: Replaced float multiplication with modulus"));
    aor2.put(Opcodes.DMUL, new InsnSubstitution(Opcodes.DREM, "AOR_2: Replaced double multiplication with modulus"));
    // Division to modulus
    aor2.put(Opcodes.IDIV, new InsnSubstitution(Opcodes.IREM, "AOR_2: Replaced integer division with modulus"));
    aor2.put(Opcodes.LDIV, new InsnSubstitution(Opcodes.LREM, "AOR_2: Replaced long division with modulus"));
    aor2.put(Opcodes.FDIV, new InsnSubstitution(Opcodes.FREM, "AOR_2: Replaced float division with modulus"));
    aor2.put(Opcodes.DDIV, new InsnSubstitution(Opcodes.DREM, "AOR_2: Replaced double division with modulus"));
    // Modulus to division
    aor2.put(Opcodes.IREM, new InsnSubstitution(Opcodes.IDIV, "AOR_2: Replaced integer modulus with division"));
    aor2.put(Opcodes.LREM, new InsnSubstitution(Opcodes.LDIV, "AOR_2: Replaced long modulus with division"));
    aor2.put(Opcodes.FREM, new InsnSubstitution(Opcodes.FDIV, "AOR_2: Replaced float modulus with division"));
    aor2.put(Opcodes.DREM, new InsnSubstitution(Opcodes.DDIV, "AOR_2: Replaced double modulus with division"));
    MUTATIONS.put(ArithmeticOperatorReplacementMutator.AOR_2, aor2);

    // AOR_3: + -> /, - -> /, * -> +, / -> +, % -> +
    Map<Integer, ZeroOperandMutation> aor3 = new HashMap<>();
    // Addition to division
    aor3.put(Opcodes.IADD, new InsnSubstitution(Opcodes.IDIV, "AOR_3: Replaced integer addition with division"));
    aor3.put(Opcodes.LADD, new InsnSubstitution(Opcodes.LDIV, "AOR_3: Replaced long addition with division"));
    aor3.put(Opcodes.FADD, new InsnSubstitution(Opcodes.FDIV, "AOR_3: Replaced float addition with division"));
    aor3.put(Opcodes.DADD, new InsnSubstitution(Opcodes.DDIV, "AOR_3: Replaced double addition with division"));
    // Subtraction to division
    aor3.put(Opcodes.ISUB, new InsnSubstitution(Opcodes.IDIV, "AOR_3: Replaced integer subtraction with division"));
    aor3.put(Opcodes.LSUB, new InsnSubstitution(Opcodes.LDIV, "AOR_3: Replaced long subtraction with division"));
    aor3.put(Opcodes.FSUB, new InsnSubstitution(Opcodes.FDIV, "AOR_3: Replaced float subtraction with division"));
    aor3.put(Opcodes.DSUB, new InsnSubstitution(Opcodes.DDIV, "AOR_3: Replaced double subtraction with division"));
    // Multiplication to addition
    aor3.put(Opcodes.IMUL, new InsnSubstitution(Opcodes.IADD, "AOR_3: Replaced integer multiplication with addition"));
    aor3.put(Opcodes.LMUL, new InsnSubstitution(Opcodes.LADD, "AOR_3: Replaced long multiplication with addition"));
    aor3.put(Opcodes.FMUL, new InsnSubstitution(Opcodes.FADD, "AOR_3: Replaced float multiplication with addition"));
    aor3.put(Opcodes.DMUL, new InsnSubstitution(Opcodes.DADD, "AOR_3: Replaced double multiplication with addition"));
    // Division to addition
    aor3.put(Opcodes.IDIV, new InsnSubstitution(Opcodes.IADD, "AOR_3: Replaced integer division with addition"));
    aor3.put(Opcodes.LDIV, new InsnSubstitution(Opcodes.LADD, "AOR_3: Replaced long division with addition"));
    aor3.put(Opcodes.FDIV, new InsnSubstitution(Opcodes.FADD, "AOR_3: Replaced float division with addition"));
    aor3.put(Opcodes.DDIV, new InsnSubstitution(Opcodes.DADD, "AOR_3: Replaced double division with addition"));
    // Modulus to addition
    aor3.put(Opcodes.IREM, new InsnSubstitution(Opcodes.IADD, "AOR_3: Replaced integer modulus with addition"));
    aor3.put(Opcodes.LREM, new InsnSubstitution(Opcodes.LADD, "AOR_3: Replaced long modulus with addition"));
    aor3.put(Opcodes.FREM, new InsnSubstitution(Opcodes.FADD, "AOR_3: Replaced float modulus with addition"));
    aor3.put(Opcodes.DREM, new InsnSubstitution(Opcodes.DADD, "AOR_3: Replaced double modulus with addition"));
    MUTATIONS.put(ArithmeticOperatorReplacementMutator.AOR_3, aor3);

    // AOR_4: + -> %, - -> %, * -> -, / -> -, % -> -
    Map<Integer, ZeroOperandMutation> aor4 = new HashMap<>();
    // Addition to modulus
    aor4.put(Opcodes.IADD, new InsnSubstitution(Opcodes.IREM, "AOR_4: Replaced integer addition with modulus"));
    aor4.put(Opcodes.LADD, new InsnSubstitution(Opcodes.LREM, "AOR_4: Replaced long addition with modulus"));
    aor4.put(Opcodes.FADD, new InsnSubstitution(Opcodes.FREM, "AOR_4: Replaced float addition with modulus"));
    aor4.put(Opcodes.DADD, new InsnSubstitution(Opcodes.DREM, "AOR_4: Replaced double addition with modulus"));
    // Subtraction to modulus
    aor4.put(Opcodes.ISUB, new InsnSubstitution(Opcodes.IREM, "AOR_4: Replaced integer subtraction with modulus"));
    aor4.put(Opcodes.LSUB, new InsnSubstitution(Opcodes.LREM, "AOR_4: Replaced long subtraction with modulus"));
    aor4.put(Opcodes.FSUB, new InsnSubstitution(Opcodes.FREM, "AOR_4: Replaced float subtraction with modulus"));
    aor4.put(Opcodes.DSUB, new InsnSubstitution(Opcodes.DREM, "AOR_4: Replaced double subtraction with modulus"));
    // Multiplication to subtraction
    aor4.put(Opcodes.IMUL, new InsnSubstitution(Opcodes.ISUB, "AOR_4: Replaced integer multiplication with subtraction"));
    aor4.put(Opcodes.LMUL, new InsnSubstitution(Opcodes.LSUB, "AOR_4: Replaced long multiplication with subtraction"));
    aor4.put(Opcodes.FMUL, new InsnSubstitution(Opcodes.FSUB, "AOR_4: Replaced float multiplication with subtraction"));
    aor4.put(Opcodes.DMUL, new InsnSubstitution(Opcodes.DSUB, "AOR_4: Replaced double multiplication with subtraction"));
    // Division to subtraction
    aor4.put(Opcodes.IDIV, new InsnSubstitution(Opcodes.ISUB, "AOR_4: Replaced integer division with subtraction"));
    aor4.put(Opcodes.LDIV, new InsnSubstitution(Opcodes.LSUB, "AOR_4: Replaced long division with subtraction"));
    aor4.put(Opcodes.FDIV, new InsnSubstitution(Opcodes.FSUB, "AOR_4: Replaced float division with subtraction"));
    aor4.put(Opcodes.DDIV, new InsnSubstitution(Opcodes.DSUB, "AOR_4: Replaced double division with subtraction"));
    // Modulus to subtraction
    aor4.put(Opcodes.IREM, new InsnSubstitution(Opcodes.ISUB, "AOR_4: Replaced integer modulus with subtraction"));
    aor4.put(Opcodes.LREM, new InsnSubstitution(Opcodes.LSUB, "AOR_4: Replaced long modulus with subtraction"));
    aor4.put(Opcodes.FREM, new InsnSubstitution(Opcodes.FSUB, "AOR_4: Replaced float modulus with subtraction"));
    aor4.put(Opcodes.DREM, new InsnSubstitution(Opcodes.DSUB, "AOR_4: Replaced double modulus with subtraction"));
    MUTATIONS.put(ArithmeticOperatorReplacementMutator.AOR_4, aor4);
  }

  @Override
  protected Map<Integer, ZeroOperandMutation> getMutations() {
    return MUTATIONS.get(mutatorType);
  }

}
