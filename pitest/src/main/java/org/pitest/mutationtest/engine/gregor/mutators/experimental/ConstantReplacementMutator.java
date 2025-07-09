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
 * Constant Replacement Mutator (CRCR)
 * 
 * This mutator replaces constant values according to:
 * CRCR_1: c -> 1
 * CRCR_2: c -> 0  
 * CRCR_3: c -> -1
 * CRCR_4: c -> -c
 * CRCR_5: c -> c+1
 * CRCR_6: c -> c-1
 */
public enum ConstantReplacementMutator implements MethodMutatorFactory {

  CRCR_1("CRCR_1", "replace with 1"),
  CRCR_2("CRCR_2", "replace with 0"),
  CRCR_3("CRCR_3", "replace with -1"),
  CRCR_4("CRCR_4", "replace with negation"),
  CRCR_5("CRCR_5", "replace with increment"),
  CRCR_6("CRCR_6", "replace with decrement");

  private final String name;
  private final String description;

  ConstantReplacementMutator(String name, String description) {
    this.name = name;
    this.description = description;
  }

  @Override
  public MethodVisitor create(final MutationContext context,
      final MethodInfo methodInfo, final MethodVisitor methodVisitor) {
    return new ConstantReplacementMethodVisitor(this, context, methodVisitor);
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

class ConstantReplacementMethodVisitor extends MethodVisitor {

  private final ConstantReplacementMutator mutatorType;
  private final MutationContext context;

  ConstantReplacementMethodVisitor(final ConstantReplacementMutator mutatorType,
      final MutationContext context, final MethodVisitor methodVisitor) {
    super(ASMVersion.ASM_VERSION, methodVisitor);
    this.mutatorType = mutatorType;
    this.context = context;
  }

  @Override
  public void visitInsn(final int opcode) {
    if (isConstantInstruction(opcode) && shouldMutate(opcode)) {
      applyConstantMutation(opcode);
    } else {
      super.visitInsn(opcode);
    }
  }

  @Override
  public void visitIntInsn(final int opcode, final int operand) {
    if ((opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) && shouldMutateInt(operand)) {
      applyIntConstantMutation(opcode, operand);
    } else {
      super.visitIntInsn(opcode, operand);
    }
  }

  @Override
  public void visitLdcInsn(final Object value) {
    if (shouldMutateLdc(value)) {
      applyLdcConstantMutation(value);
    } else {
      super.visitLdcInsn(value);
    }
  }

  private boolean isConstantInstruction(int opcode) {
    return (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5)
           || (opcode >= Opcodes.LCONST_0 && opcode <= Opcodes.LCONST_1)
           || (opcode >= Opcodes.FCONST_0 && opcode <= Opcodes.FCONST_2)
           || (opcode >= Opcodes.DCONST_0 && opcode <= Opcodes.DCONST_1);
  }

  private void applyConstantMutation(int originalOpcode) {
    switch (mutatorType) {
      case CRCR_1: // Replace with 1
        if (originalOpcode >= Opcodes.ICONST_M1 && originalOpcode <= Opcodes.ICONST_5) {
          super.visitInsn(Opcodes.ICONST_1);
        } else if (originalOpcode >= Opcodes.LCONST_0 && originalOpcode <= Opcodes.LCONST_1) {
          super.visitInsn(Opcodes.LCONST_1);
        } else if (originalOpcode >= Opcodes.FCONST_0 && originalOpcode <= Opcodes.FCONST_2) {
          super.visitInsn(Opcodes.FCONST_1);
        } else if (originalOpcode >= Opcodes.DCONST_0 && originalOpcode <= Opcodes.DCONST_1) {
          super.visitInsn(Opcodes.DCONST_1);
        }
        break;
      case CRCR_2: // Replace with 0
        if (originalOpcode >= Opcodes.ICONST_M1 && originalOpcode <= Opcodes.ICONST_5) {
          super.visitInsn(Opcodes.ICONST_0);
        } else if (originalOpcode >= Opcodes.LCONST_0 && originalOpcode <= Opcodes.LCONST_1) {
          super.visitInsn(Opcodes.LCONST_0);
        } else if (originalOpcode >= Opcodes.FCONST_0 && originalOpcode <= Opcodes.FCONST_2) {
          super.visitInsn(Opcodes.FCONST_0);
        } else if (originalOpcode >= Opcodes.DCONST_0 && originalOpcode <= Opcodes.DCONST_1) {
          super.visitInsn(Opcodes.DCONST_0);
        }
        break;
      case CRCR_3: // Replace with -1
        if (originalOpcode >= Opcodes.ICONST_M1 && originalOpcode <= Opcodes.ICONST_5) {
          super.visitInsn(Opcodes.ICONST_M1);
        } else if (originalOpcode >= Opcodes.LCONST_0 && originalOpcode <= Opcodes.LCONST_1) {
          super.visitInsn(Opcodes.ICONST_M1);
          super.visitInsn(Opcodes.I2L);
        } else if (originalOpcode >= Opcodes.FCONST_0 && originalOpcode <= Opcodes.FCONST_2) {
          super.visitInsn(Opcodes.ICONST_M1);
          super.visitInsn(Opcodes.I2F);
        } else if (originalOpcode >= Opcodes.DCONST_0 && originalOpcode <= Opcodes.DCONST_1) {
          super.visitInsn(Opcodes.ICONST_M1);
          super.visitInsn(Opcodes.I2D);
        }
        break;
      case CRCR_4: // Replace with negation
        handleNegationMutation(originalOpcode);
        break;
      case CRCR_5: // Replace with increment
        handleIncrementMutation(originalOpcode);
        break;
      case CRCR_6: // Replace with decrement
        handleDecrementMutation(originalOpcode);
        break;
    }
  }

  private void applyIntConstantMutation(int opcode, int originalValue) {
    switch (mutatorType) {
      case CRCR_1: // Replace with 1
        super.visitInsn(Opcodes.ICONST_1);
        break;
      case CRCR_2: // Replace with 0
        super.visitInsn(Opcodes.ICONST_0);
        break;
      case CRCR_3: // Replace with -1
        super.visitInsn(Opcodes.ICONST_M1);
        break;
      case CRCR_4: // Replace with negation
        super.visitIntInsn(opcode, -originalValue);
        break;
      case CRCR_5: // Replace with increment
        super.visitIntInsn(opcode, originalValue + 1);
        break;
      case CRCR_6: // Replace with decrement
        super.visitIntInsn(opcode, originalValue - 1);
        break;
    }
  }

  private void applyLdcConstantMutation(Object originalValue) {
    switch (mutatorType) {
      case CRCR_1: // Replace with 1
        if (originalValue instanceof Integer) {
          super.visitLdcInsn(1);
        } else if (originalValue instanceof Long) {
          super.visitLdcInsn(1L);
        } else if (originalValue instanceof Float) {
          super.visitLdcInsn(1.0f);
        } else if (originalValue instanceof Double) {
          super.visitLdcInsn(1.0);
        } else {
          super.visitLdcInsn(originalValue);
        }
        break;
      case CRCR_2: // Replace with 0
        if (originalValue instanceof Integer) {
          super.visitLdcInsn(0);
        } else if (originalValue instanceof Long) {
          super.visitLdcInsn(0L);
        } else if (originalValue instanceof Float) {
          super.visitLdcInsn(0.0f);
        } else if (originalValue instanceof Double) {
          super.visitLdcInsn(0.0);
        } else {
          super.visitLdcInsn(originalValue);
        }
        break;
      case CRCR_3: // Replace with -1
        if (originalValue instanceof Integer) {
          super.visitLdcInsn(-1);
        } else if (originalValue instanceof Long) {
          super.visitLdcInsn(-1L);
        } else if (originalValue instanceof Float) {
          super.visitLdcInsn(-1.0f);
        } else if (originalValue instanceof Double) {
          super.visitLdcInsn(-1.0);
        } else {
          super.visitLdcInsn(originalValue);
        }
        break;
      case CRCR_4: // Replace with negation
        if (originalValue instanceof Integer) {
          super.visitLdcInsn(-(Integer)originalValue);
        } else if (originalValue instanceof Long) {
          super.visitLdcInsn(-(Long)originalValue);
        } else if (originalValue instanceof Float) {
          super.visitLdcInsn(-(Float)originalValue);
        } else if (originalValue instanceof Double) {
          super.visitLdcInsn(-(Double)originalValue);
        } else {
          super.visitLdcInsn(originalValue);
        }
        break;
      case CRCR_5: // Replace with increment
        if (originalValue instanceof Integer) {
          super.visitLdcInsn((Integer)originalValue + 1);
        } else if (originalValue instanceof Long) {
          super.visitLdcInsn((Long)originalValue + 1L);
        } else if (originalValue instanceof Float) {
          super.visitLdcInsn((Float)originalValue + 1.0f);
        } else if (originalValue instanceof Double) {
          super.visitLdcInsn((Double)originalValue + 1.0);
        } else {
          super.visitLdcInsn(originalValue);
        }
        break;
      case CRCR_6: // Replace with decrement
        if (originalValue instanceof Integer) {
          super.visitLdcInsn((Integer)originalValue - 1);
        } else if (originalValue instanceof Long) {
          super.visitLdcInsn((Long)originalValue - 1L);
        } else if (originalValue instanceof Float) {
          super.visitLdcInsn((Float)originalValue - 1.0f);
        } else if (originalValue instanceof Double) {
          super.visitLdcInsn((Double)originalValue - 1.0);
        } else {
          super.visitLdcInsn(originalValue);
        }
        break;
    }
  }

  private void handleNegationMutation(int originalOpcode) {
    if (originalOpcode >= Opcodes.ICONST_M1 && originalOpcode <= Opcodes.ICONST_5) {
      int value = originalOpcode - Opcodes.ICONST_0;
      if (originalOpcode == Opcodes.ICONST_M1) {
        value = -1;
      }
      super.visitIntInsn(Opcodes.BIPUSH, -value);
    } else {
      // For other constants, load original and negate
      super.visitInsn(originalOpcode);
      if (originalOpcode >= Opcodes.LCONST_0 && originalOpcode <= Opcodes.LCONST_1) {
        super.visitInsn(Opcodes.LNEG);
      } else if (originalOpcode >= Opcodes.FCONST_0 && originalOpcode <= Opcodes.FCONST_2) {
        super.visitInsn(Opcodes.FNEG);
      } else if (originalOpcode >= Opcodes.DCONST_0 && originalOpcode <= Opcodes.DCONST_1) {
        super.visitInsn(Opcodes.DNEG);
      } else {
        super.visitInsn(Opcodes.INEG);
      }
    }
  }

  private void handleIncrementMutation(int originalOpcode) {
    if (originalOpcode >= Opcodes.ICONST_M1 && originalOpcode <= Opcodes.ICONST_5) {
      int value = originalOpcode - Opcodes.ICONST_0;
      if (originalOpcode == Opcodes.ICONST_M1) {
        value = -1;
      }
      super.visitIntInsn(Opcodes.BIPUSH, value + 1);
    } else {
      // Load original, load 1, add
      super.visitInsn(originalOpcode);
      if (originalOpcode >= Opcodes.LCONST_0 && originalOpcode <= Opcodes.LCONST_1) {
        super.visitInsn(Opcodes.LCONST_1);
        super.visitInsn(Opcodes.LADD);
      } else if (originalOpcode >= Opcodes.FCONST_0 && originalOpcode <= Opcodes.FCONST_2) {
        super.visitInsn(Opcodes.FCONST_1);
        super.visitInsn(Opcodes.FADD);
      } else if (originalOpcode >= Opcodes.DCONST_0 && originalOpcode <= Opcodes.DCONST_1) {
        super.visitInsn(Opcodes.DCONST_1);
        super.visitInsn(Opcodes.DADD);
      } else {
        super.visitInsn(Opcodes.ICONST_1);
        super.visitInsn(Opcodes.IADD);
      }
    }
  }

  private void handleDecrementMutation(int originalOpcode) {
    if (originalOpcode >= Opcodes.ICONST_M1 && originalOpcode <= Opcodes.ICONST_5) {
      int value = originalOpcode - Opcodes.ICONST_0;
      if (originalOpcode == Opcodes.ICONST_M1) {
        value = -1;
      }
      super.visitIntInsn(Opcodes.BIPUSH, value - 1);
    } else {
      // Load original, load 1, subtract
      super.visitInsn(originalOpcode);
      if (originalOpcode >= Opcodes.LCONST_0 && originalOpcode <= Opcodes.LCONST_1) {
        super.visitInsn(Opcodes.LCONST_1);
        super.visitInsn(Opcodes.LSUB);
      } else if (originalOpcode >= Opcodes.FCONST_0 && originalOpcode <= Opcodes.FCONST_2) {
        super.visitInsn(Opcodes.FCONST_1);
        super.visitInsn(Opcodes.FSUB);
      } else if (originalOpcode >= Opcodes.DCONST_0 && originalOpcode <= Opcodes.DCONST_1) {
        super.visitInsn(Opcodes.DCONST_1);
        super.visitInsn(Opcodes.DSUB);
      } else {
        super.visitInsn(Opcodes.ICONST_1);
        super.visitInsn(Opcodes.ISUB);
      }
    }
  }

  private boolean shouldMutate(int opcode) {
    String description = mutatorType.getName() + ": " + mutatorType.getDescription() + " for constant instruction";
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

  private boolean shouldMutateInt(int value) {
    String description = mutatorType.getName() + ": " + mutatorType.getDescription() + " for integer constant " + value;
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

  private boolean shouldMutateLdc(Object value) {
    String description = mutatorType.getName() + ": " + mutatorType.getDescription() + " for constant " + value;
    
    final MutationIdentifier mutationId = this.context.registerMutation(
        mutatorType, description);
    return this.context.shouldMutate(mutationId);
  }

}
