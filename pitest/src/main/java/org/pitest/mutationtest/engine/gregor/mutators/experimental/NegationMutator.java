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
 * Negation Mutator (ABS)
 * 
 * This mutator replaces any use of a numeric variable with its negation.
 * For example: return i; becomes return -i;
 */
public enum NegationMutator implements MethodMutatorFactory {

  ABS;

  @Override
  public MethodVisitor create(final MutationContext context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new NegationMethodVisitor(this, context, methodVisitor);
  }

  @Override
  public String getGloballyUniqueId() {
    return this.getClass().getName();
  }

  @Override
  public String getName() {
    return name();
  }

}

class NegationMethodVisitor extends MethodVisitor {

  private final NegationMutator mutatorType;
  private final MutationContext context;

  NegationMethodVisitor(final NegationMutator mutatorType,
      final MutationContext context, final MethodVisitor methodVisitor) {
    super(ASMVersion.ASM_VERSION, methodVisitor);
    this.mutatorType = mutatorType;
    this.context = context;
  }

  @Override
  public void visitVarInsn(final int opcode, final int var) {
    // Handle local variable load instructions that should be negated
    if (isNumericVariableLoad(opcode) && shouldMutate(var)) {
      super.visitVarInsn(opcode, var);
      applyNegation(opcode);
    } else {
      super.visitVarInsn(opcode, var);
    }
  }

  @Override
  public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
    // Handle field access instructions that should be negated
    if (opcode == Opcodes.GETFIELD && isNumericType(desc) && shouldMutateField(name)) {
      super.visitFieldInsn(opcode, owner, name, desc);
      applyNegationByDescriptor(desc);
    } else {
      super.visitFieldInsn(opcode, owner, name, desc);
    }
  }

  @Override
  public void visitInsn(final int opcode) {
    // Handle array load instructions that should be negated
    if (isArrayLoad(opcode) && shouldMutateArrayLoad(opcode)) {
      super.visitInsn(opcode);
      applyNegationForArrayLoad(opcode);
    } else {
      super.visitInsn(opcode);
    }
  }

  private boolean isNumericVariableLoad(int opcode) {
    return opcode == Opcodes.ILOAD || opcode == Opcodes.LLOAD
           || opcode == Opcodes.FLOAD || opcode == Opcodes.DLOAD;
  }

  private boolean isArrayLoad(int opcode) {
    return opcode == Opcodes.IALOAD || opcode == Opcodes.LALOAD
           || opcode == Opcodes.FALOAD || opcode == Opcodes.DALOAD;
  }

  private boolean isNumericType(String descriptor) {
    return "I".equals(descriptor) || "J".equals(descriptor)
           || "F".equals(descriptor) || "D".equals(descriptor);
  }

  private void applyNegation(int loadOpcode) {
    switch (loadOpcode) {
      case Opcodes.ILOAD:
        super.visitInsn(Opcodes.INEG);
        break;
      case Opcodes.LLOAD:
        super.visitInsn(Opcodes.LNEG);
        break;
      case Opcodes.FLOAD:
        super.visitInsn(Opcodes.FNEG);
        break;
      case Opcodes.DLOAD:
        super.visitInsn(Opcodes.DNEG);
        break;
    }
  }

  private void applyNegationByDescriptor(String descriptor) {
    switch (descriptor) {
      case "I":
        super.visitInsn(Opcodes.INEG);
        break;
      case "J":
        super.visitInsn(Opcodes.LNEG);
        break;
      case "F":
        super.visitInsn(Opcodes.FNEG);
        break;
      case "D":
        super.visitInsn(Opcodes.DNEG);
        break;
    }
  }

  private void applyNegationForArrayLoad(int arrayLoadOpcode) {
    switch (arrayLoadOpcode) {
      case Opcodes.IALOAD:
        super.visitInsn(Opcodes.INEG);
        break;
      case Opcodes.LALOAD:
        super.visitInsn(Opcodes.LNEG);
        break;
      case Opcodes.FALOAD:
        super.visitInsn(Opcodes.FNEG);
        break;
      case Opcodes.DALOAD:
        super.visitInsn(Opcodes.DNEG);
        break;
    }
  }

  private boolean shouldMutate(int var) {
    String description = "ABS: Applied negation to local variable " + var;
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

  private boolean shouldMutateField(String fieldName) {
    String description = "ABS: Applied negation to field " + fieldName;
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

  private boolean shouldMutateArrayLoad(int opcode) {
    String description = "ABS: Applied negation to array load operation";
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

}
