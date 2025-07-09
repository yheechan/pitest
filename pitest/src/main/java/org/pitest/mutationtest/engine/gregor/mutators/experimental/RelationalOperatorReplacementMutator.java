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
import org.pitest.mutationtest.engine.gregor.AbstractJumpMutator;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;

/**
 * Relational Operator Replacement Mutator (ROR)
 * 
 * This mutator replaces relational operators according to:
 * ROR_1 to ROR_5: Each sub-mutator handles different replacement patterns
 */
public enum RelationalOperatorReplacementMutator implements MethodMutatorFactory {

  ROR_1("ROR_1"),
  ROR_2("ROR_2"),
  ROR_3("ROR_3"),
  ROR_4("ROR_4"),
  ROR_5("ROR_5");

  private final String name;

  RelationalOperatorReplacementMutator(String name) {
    this.name = name;
  }

  @Override
  public MethodVisitor create(final MutationContext context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new RelationalOperatorReplacementMethodVisitor(this, methodInfo, context, methodVisitor);
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

class RelationalOperatorReplacementMethodVisitor extends AbstractJumpMutator {

  private final RelationalOperatorReplacementMutator mutatorType;
  private static final Map<RelationalOperatorReplacementMutator, Map<Integer, AbstractJumpMutator.Substitution>> MUTATIONS = new HashMap<>();

  static {
    // ROR_1: < -> <=, <= -> <, > -> >=, >= -> >, == -> <, != -> <
    Map<Integer, AbstractJumpMutator.Substitution> ror1 = new HashMap<>();
    ror1.put(Opcodes.IF_ICMPLT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLE, "ROR_1: Replaced < with <="));
    ror1.put(Opcodes.IF_ICMPLE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLT, "ROR_1: Replaced <= with <"));
    ror1.put(Opcodes.IF_ICMPGT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGE, "ROR_1: Replaced > with >="));
    ror1.put(Opcodes.IF_ICMPGE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGT, "ROR_1: Replaced >= with >"));
    ror1.put(Opcodes.IF_ICMPEQ, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLT, "ROR_1: Replaced == with <"));
    ror1.put(Opcodes.IF_ICMPNE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLT, "ROR_1: Replaced != with <"));
    ror1.put(Opcodes.IFLT, new AbstractJumpMutator.Substitution(Opcodes.IFLE, "ROR_1: Replaced < with <="));
    ror1.put(Opcodes.IFLE, new AbstractJumpMutator.Substitution(Opcodes.IFLT, "ROR_1: Replaced <= with <"));
    ror1.put(Opcodes.IFGT, new AbstractJumpMutator.Substitution(Opcodes.IFGE, "ROR_1: Replaced > with >="));
    ror1.put(Opcodes.IFGE, new AbstractJumpMutator.Substitution(Opcodes.IFGT, "ROR_1: Replaced >= with >"));
    ror1.put(Opcodes.IFEQ, new AbstractJumpMutator.Substitution(Opcodes.IFLT, "ROR_1: Replaced == with <"));
    ror1.put(Opcodes.IFNE, new AbstractJumpMutator.Substitution(Opcodes.IFLT, "ROR_1: Replaced != with <"));
    MUTATIONS.put(RelationalOperatorReplacementMutator.ROR_1, ror1);

    // ROR_2: < -> >, <= -> >, > -> <=, >= -> <=, == -> <=, != -> <=
    Map<Integer, AbstractJumpMutator.Substitution> ror2 = new HashMap<>();
    ror2.put(Opcodes.IF_ICMPLT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGT, "ROR_2: Replaced < with >"));
    ror2.put(Opcodes.IF_ICMPLE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGT, "ROR_2: Replaced <= with >"));
    ror2.put(Opcodes.IF_ICMPGT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLE, "ROR_2: Replaced > with <="));
    ror2.put(Opcodes.IF_ICMPGE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLE, "ROR_2: Replaced >= with <="));
    ror2.put(Opcodes.IF_ICMPEQ, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLE, "ROR_2: Replaced == with <="));
    ror2.put(Opcodes.IF_ICMPNE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPLE, "ROR_2: Replaced != with <="));
    ror2.put(Opcodes.IFLT, new AbstractJumpMutator.Substitution(Opcodes.IFGT, "ROR_2: Replaced < with >"));
    ror2.put(Opcodes.IFLE, new AbstractJumpMutator.Substitution(Opcodes.IFGT, "ROR_2: Replaced <= with >"));
    ror2.put(Opcodes.IFGT, new AbstractJumpMutator.Substitution(Opcodes.IFLE, "ROR_2: Replaced > with <="));
    ror2.put(Opcodes.IFGE, new AbstractJumpMutator.Substitution(Opcodes.IFLE, "ROR_2: Replaced >= with <="));
    ror2.put(Opcodes.IFEQ, new AbstractJumpMutator.Substitution(Opcodes.IFLE, "ROR_2: Replaced == with <="));
    ror2.put(Opcodes.IFNE, new AbstractJumpMutator.Substitution(Opcodes.IFLE, "ROR_2: Replaced != with <="));
    MUTATIONS.put(RelationalOperatorReplacementMutator.ROR_2, ror2);

    // ROR_3: < -> >=, <= -> >=, > -> >=, >= -> >, == -> >, != -> >
    Map<Integer, AbstractJumpMutator.Substitution> ror3 = new HashMap<>();
    ror3.put(Opcodes.IF_ICMPLT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGE, "ROR_3: Replaced < with >="));
    ror3.put(Opcodes.IF_ICMPLE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGE, "ROR_3: Replaced <= with >="));
    ror3.put(Opcodes.IF_ICMPGT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGE, "ROR_3: Replaced > with >="));
    ror3.put(Opcodes.IF_ICMPGE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGT, "ROR_3: Replaced >= with >"));
    ror3.put(Opcodes.IF_ICMPEQ, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGT, "ROR_3: Replaced == with >"));
    ror3.put(Opcodes.IF_ICMPNE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGT, "ROR_3: Replaced != with >"));
    ror3.put(Opcodes.IFLT, new AbstractJumpMutator.Substitution(Opcodes.IFGE, "ROR_3: Replaced < with >="));
    ror3.put(Opcodes.IFLE, new AbstractJumpMutator.Substitution(Opcodes.IFGE, "ROR_3: Replaced <= with >="));
    ror3.put(Opcodes.IFGT, new AbstractJumpMutator.Substitution(Opcodes.IFGE, "ROR_3: Replaced > with >="));
    ror3.put(Opcodes.IFGE, new AbstractJumpMutator.Substitution(Opcodes.IFGT, "ROR_3: Replaced >= with >"));
    ror3.put(Opcodes.IFEQ, new AbstractJumpMutator.Substitution(Opcodes.IFGT, "ROR_3: Replaced == with >"));
    ror3.put(Opcodes.IFNE, new AbstractJumpMutator.Substitution(Opcodes.IFGT, "ROR_3: Replaced != with >"));
    MUTATIONS.put(RelationalOperatorReplacementMutator.ROR_3, ror3);

    // ROR_4: < -> ==, <= -> ==, > -> ==, >= -> ==, == -> >=, != -> >=
    Map<Integer, AbstractJumpMutator.Substitution> ror4 = new HashMap<>();
    ror4.put(Opcodes.IF_ICMPLT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPEQ, "ROR_4: Replaced < with =="));
    ror4.put(Opcodes.IF_ICMPLE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPEQ, "ROR_4: Replaced <= with =="));
    ror4.put(Opcodes.IF_ICMPGT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPEQ, "ROR_4: Replaced > with =="));
    ror4.put(Opcodes.IF_ICMPGE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPEQ, "ROR_4: Replaced >= with =="));
    ror4.put(Opcodes.IF_ICMPEQ, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGE, "ROR_4: Replaced == with >="));
    ror4.put(Opcodes.IF_ICMPNE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPGE, "ROR_4: Replaced != with >="));
    ror4.put(Opcodes.IFLT, new AbstractJumpMutator.Substitution(Opcodes.IFEQ, "ROR_4: Replaced < with =="));
    ror4.put(Opcodes.IFLE, new AbstractJumpMutator.Substitution(Opcodes.IFEQ, "ROR_4: Replaced <= with =="));
    ror4.put(Opcodes.IFGT, new AbstractJumpMutator.Substitution(Opcodes.IFEQ, "ROR_4: Replaced > with =="));
    ror4.put(Opcodes.IFGE, new AbstractJumpMutator.Substitution(Opcodes.IFEQ, "ROR_4: Replaced >= with =="));
    ror4.put(Opcodes.IFEQ, new AbstractJumpMutator.Substitution(Opcodes.IFGE, "ROR_4: Replaced == with >="));
    ror4.put(Opcodes.IFNE, new AbstractJumpMutator.Substitution(Opcodes.IFGE, "ROR_4: Replaced != with >="));
    MUTATIONS.put(RelationalOperatorReplacementMutator.ROR_4, ror4);

    // ROR_5: < -> !=, <= -> !=, > -> !=, >= -> !=, == -> !=, != -> ==
    Map<Integer, AbstractJumpMutator.Substitution> ror5 = new HashMap<>();
    ror5.put(Opcodes.IF_ICMPLT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPNE, "ROR_5: Replaced < with !="));
    ror5.put(Opcodes.IF_ICMPLE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPNE, "ROR_5: Replaced <= with !="));
    ror5.put(Opcodes.IF_ICMPGT, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPNE, "ROR_5: Replaced > with !="));
    ror5.put(Opcodes.IF_ICMPGE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPNE, "ROR_5: Replaced >= with !="));
    ror5.put(Opcodes.IF_ICMPEQ, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPNE, "ROR_5: Replaced == with !="));
    ror5.put(Opcodes.IF_ICMPNE, new AbstractJumpMutator.Substitution(Opcodes.IF_ICMPEQ, "ROR_5: Replaced != with =="));
    ror5.put(Opcodes.IFLT, new AbstractJumpMutator.Substitution(Opcodes.IFNE, "ROR_5: Replaced < with !="));
    ror5.put(Opcodes.IFLE, new AbstractJumpMutator.Substitution(Opcodes.IFNE, "ROR_5: Replaced <= with !="));
    ror5.put(Opcodes.IFGT, new AbstractJumpMutator.Substitution(Opcodes.IFNE, "ROR_5: Replaced > with !="));
    ror5.put(Opcodes.IFGE, new AbstractJumpMutator.Substitution(Opcodes.IFNE, "ROR_5: Replaced >= with !="));
    ror5.put(Opcodes.IFEQ, new AbstractJumpMutator.Substitution(Opcodes.IFNE, "ROR_5: Replaced == with !="));
    ror5.put(Opcodes.IFNE, new AbstractJumpMutator.Substitution(Opcodes.IFEQ, "ROR_5: Replaced != with =="));
    MUTATIONS.put(RelationalOperatorReplacementMutator.ROR_5, ror5);
  }

  RelationalOperatorReplacementMethodVisitor(final RelationalOperatorReplacementMutator mutatorType,
      final MethodInfo methodInfo, final MutationContext context,
      final MethodVisitor writer) {
    super(mutatorType, context, writer);
    this.mutatorType = mutatorType;
  }

  @Override
  protected Map<Integer, AbstractJumpMutator.Substitution> getMutations() {
    return MUTATIONS.get(mutatorType);
  }

}
