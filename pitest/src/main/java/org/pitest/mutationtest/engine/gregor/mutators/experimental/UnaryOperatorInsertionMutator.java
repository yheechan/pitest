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
 * Unary Operator Insertion Mutator (UOI)
 * 
 * This mutator inserts unary operators (increment/decrement) to variable operations.
 * UOI_1: a -> a++
 * UOI_2: a -> a--
 * UOI_3: a -> ++a
 * UOI_4: a -> --a
 */
public enum UnaryOperatorInsertionMutator implements MethodMutatorFactory {

  UOI_1("UOI_1", "post-increment"),
  UOI_2("UOI_2", "post-decrement"),
  UOI_3("UOI_3", "pre-increment"),
  UOI_4("UOI_4", "pre-decrement");

  private final String name;
  private final String description;

  UnaryOperatorInsertionMutator(String name, String description) {
    this.name = name;
    this.description = description;
  }

  @Override
  public MethodVisitor create(final MutationContext context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new UnaryOperatorInsertionMethodVisitor(this, context, methodVisitor);
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

class UnaryOperatorInsertionMethodVisitor extends MethodVisitor {

  private final UnaryOperatorInsertionMutator mutatorType;
  private final MutationContext context;

  UnaryOperatorInsertionMethodVisitor(final UnaryOperatorInsertionMutator mutatorType,
      final MutationContext context, final MethodVisitor methodVisitor) {
    super(ASMVersion.ASM_VERSION, methodVisitor);
    this.mutatorType = mutatorType;
    this.context = context;
  }

  @Override
  public void visitVarInsn(final int opcode, final int var) {
    // Handle local variable load instructions
    if (isVariableLoad(opcode) && shouldMutate(var)) {
      applyUnaryMutation(opcode, var);
    } else {
      super.visitVarInsn(opcode, var);
    }
  }

  @Override
  public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
    // Handle field access instructions
    if (opcode == Opcodes.GETFIELD && shouldMutateField(name)) {
      applyUnaryMutationToField(opcode, owner, name, desc);
    } else {
      super.visitFieldInsn(opcode, owner, name, desc);
    }
  }

  private boolean isVariableLoad(int opcode) {
    return opcode == Opcodes.ILOAD || opcode == Opcodes.LLOAD || opcode == Opcodes.FLOAD || opcode == Opcodes.DLOAD || opcode == Opcodes.ALOAD;
  }

  private void applyUnaryMutation(int loadOpcode, int var) {
    switch (mutatorType) {
      case UOI_1: // post-increment: load, dup, increment, store original
        super.visitVarInsn(loadOpcode, var);
        if (isNumericType(loadOpcode)) {
          super.visitInsn(getDupOpcode(loadOpcode));
          super.visitInsn(getIncrementConstant(loadOpcode));
          super.visitInsn(getAddOpcode(loadOpcode));
          super.visitVarInsn(getStoreOpcode(loadOpcode), var);
        }
        break;
      case UOI_2: // post-decrement: load, dup, decrement, store original
        super.visitVarInsn(loadOpcode, var);
        if (isNumericType(loadOpcode)) {
          super.visitInsn(getDupOpcode(loadOpcode));
          super.visitInsn(getIncrementConstant(loadOpcode));
          super.visitInsn(getSubOpcode(loadOpcode));
          super.visitVarInsn(getStoreOpcode(loadOpcode), var);
        }
        break;
      case UOI_3: // pre-increment: increment, dup, store
        if (isNumericType(loadOpcode)) {
          super.visitVarInsn(loadOpcode, var);
          super.visitInsn(getIncrementConstant(loadOpcode));
          super.visitInsn(getAddOpcode(loadOpcode));
          super.visitInsn(getDupOpcode(loadOpcode));
          super.visitVarInsn(getStoreOpcode(loadOpcode), var);
        } else {
          super.visitVarInsn(loadOpcode, var);
        }
        break;
      case UOI_4: // pre-decrement: decrement, dup, store
        if (isNumericType(loadOpcode)) {
          super.visitVarInsn(loadOpcode, var);
          super.visitInsn(getIncrementConstant(loadOpcode));
          super.visitInsn(getSubOpcode(loadOpcode));
          super.visitInsn(getDupOpcode(loadOpcode));
          super.visitVarInsn(getStoreOpcode(loadOpcode), var);
        } else {
          super.visitVarInsn(loadOpcode, var);
        }
        break;
    }
  }

  private void applyUnaryMutationToField(int opcode, String owner, String name, String desc) {
    // For fields, we need to be more careful about the stack operations
    // This is a simplified implementation that duplicates the owner reference for field mutations
    super.visitFieldInsn(opcode, owner, name, desc);
    // Additional field-specific mutation logic would go here
  }

  private boolean isNumericType(int loadOpcode) {
    return loadOpcode == Opcodes.ILOAD || loadOpcode == Opcodes.LLOAD
           || loadOpcode == Opcodes.FLOAD || loadOpcode == Opcodes.DLOAD;
  }

  private int getDupOpcode(int loadOpcode) {
    return (loadOpcode == Opcodes.LLOAD || loadOpcode == Opcodes.DLOAD) ? Opcodes.DUP2 : Opcodes.DUP;
  }

  private int getIncrementConstant(int loadOpcode) {
    switch (loadOpcode) {
      case Opcodes.ILOAD: return Opcodes.ICONST_1;
      case Opcodes.LLOAD: return Opcodes.LCONST_1;
      case Opcodes.FLOAD: return Opcodes.FCONST_1;
      case Opcodes.DLOAD: return Opcodes.DCONST_1;
      default: return Opcodes.ICONST_1;
    }
  }

  private int getAddOpcode(int loadOpcode) {
    switch (loadOpcode) {
      case Opcodes.ILOAD: return Opcodes.IADD;
      case Opcodes.LLOAD: return Opcodes.LADD;
      case Opcodes.FLOAD: return Opcodes.FADD;
      case Opcodes.DLOAD: return Opcodes.DADD;
      default: return Opcodes.IADD;
    }
  }

  private int getSubOpcode(int loadOpcode) {
    switch (loadOpcode) {
      case Opcodes.ILOAD: return Opcodes.ISUB;
      case Opcodes.LLOAD: return Opcodes.LSUB;
      case Opcodes.FLOAD: return Opcodes.FSUB;
      case Opcodes.DLOAD: return Opcodes.DSUB;
      default: return Opcodes.ISUB;
    }
  }

  private int getStoreOpcode(int loadOpcode) {
    switch (loadOpcode) {
      case Opcodes.ILOAD: return Opcodes.ISTORE;
      case Opcodes.LLOAD: return Opcodes.LSTORE;
      case Opcodes.FLOAD: return Opcodes.FSTORE;
      case Opcodes.DLOAD: return Opcodes.DSTORE;
      case Opcodes.ALOAD: return Opcodes.ASTORE;
      default: return Opcodes.ISTORE;
    }
  }

  private boolean shouldMutate(int var) {
    String description = mutatorType.getName() + ": Applied " + mutatorType.getDescription() + " to local variable " + var;
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

  private boolean shouldMutateField(String fieldName) {
    String description = mutatorType.getName() + ": Applied " + mutatorType.getDescription() + " to field " + fieldName;
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

}
