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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.pitest.bytecode.ASMVersion;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;

/**
 * Bitwise Operator Mutator (OBBN)
 * 
 * This mutator mutates bitwise operators:
 * OBBN_1: Reverse operators (& <-> |)
 * OBBN_2: Replace operation with first operand
 * OBBN_3: Replace operation with second operand
 */
public enum BitwiseOperatorMutator implements MethodMutatorFactory {

  OBBN_1("OBBN_1", "reverse bitwise operators"),
  OBBN_2("OBBN_2", "replace with first operand"),
  OBBN_3("OBBN_3", "replace with second operand");

  private final String name;
  private final String description;

  BitwiseOperatorMutator(String name, String description) {
    this.name = name;
    this.description = description;
  }

  @Override
  public MethodVisitor create(final MutationContext context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new BitwiseOperatorMethodVisitor(this, context, methodVisitor);
  }

  @Override
  public String getGloballyUniqueId() {
    return this.getClass().getName() + "_" + name;
  }

  @Override
  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

}

class BitwiseOperatorMethodVisitor extends MethodVisitor {

  private final BitwiseOperatorMutator mutatorType;
  private final MutationContext context;

  BitwiseOperatorMethodVisitor(final BitwiseOperatorMutator mutatorType,
      final MutationContext context, final MethodVisitor methodVisitor) {
    super(ASMVersion.ASM_VERSION, methodVisitor);
    this.mutatorType = mutatorType;
    this.context = context;
  }

  @Override
  public void visitInsn(final int opcode) {
    if (isBitwiseOperation(opcode) && shouldMutate(opcode)) {
      applyBitwiseMutation(opcode);
    } else {
      super.visitInsn(opcode);
    }
  }

  private boolean isBitwiseOperation(int opcode) {
    return opcode == Opcodes.IAND || opcode == Opcodes.IOR || opcode == Opcodes.IXOR
           || opcode == Opcodes.LAND || opcode == Opcodes.LOR || opcode == Opcodes.LXOR;
  }

  private void applyBitwiseMutation(int originalOpcode) {
    switch (mutatorType) {
      case OBBN_1: // Reverse operators
        switch (originalOpcode) {
          case Opcodes.IAND:
            super.visitInsn(Opcodes.IOR);
            break;
          case Opcodes.IOR:
            super.visitInsn(Opcodes.IAND);
            break;
          case Opcodes.IXOR:
            super.visitInsn(Opcodes.IAND); // XOR -> AND
            break;
          case Opcodes.LAND:
            super.visitInsn(Opcodes.LOR);
            break;
          case Opcodes.LOR:
            super.visitInsn(Opcodes.LAND);
            break;
          case Opcodes.LXOR:
            super.visitInsn(Opcodes.LAND); // XOR -> AND
            break;
          default:
            super.visitInsn(originalOpcode);
        }
        break;
      case OBBN_2: // Replace with first operand (pop second operand)
        if (isCategory2Operation(originalOpcode)) {
          super.visitInsn(Opcodes.POP2); // pop second operand (category 2)
        } else {
          super.visitInsn(Opcodes.POP); // pop second operand (category 1)
        }
        break;
      case OBBN_3: // Replace with second operand (swap and pop first operand)
        if (isCategory2Operation(originalOpcode)) {
          super.visitInsn(Opcodes.DUP2_X2); // duplicate top 2 stack slots and insert them 4 slots down
          super.visitInsn(Opcodes.POP2);    // pop duplicated values
          super.visitInsn(Opcodes.POP2);    // pop first operand
        } else {
          super.visitInsn(Opcodes.SWAP); // swap operands
          super.visitInsn(Opcodes.POP);  // pop first operand
        }
        break;
    }
  }

  private boolean isCategory2Operation(int opcode) {
    // long operations use category 2 computational types
    return opcode == Opcodes.LAND || opcode == Opcodes.LOR || opcode == Opcodes.LXOR;
  }

  private boolean shouldMutate(int opcode) {
    String operationName = getOperationName(opcode);
    String description = mutatorType.getName() + ": " + mutatorType.getDescription() + " for " + operationName;
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

  private String getOperationName(int opcode) {
    switch (opcode) {
      case Opcodes.IAND: case Opcodes.LAND: return "bitwise AND";
      case Opcodes.IOR: case Opcodes.LOR: return "bitwise OR";
      case Opcodes.IXOR: case Opcodes.LXOR: return "bitwise XOR";
      default: return "bitwise operation";
    }
  }

}
