//
//   Copyright 2016  Cityzen Data
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.script.binary;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

import java.math.BigDecimal;


/**
 * Checks the two operands on top of the stack for equality
 */
public class EQ extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public EQ(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object op2 = stack.pop();
    Object op1 = stack.pop();

    if (op2 instanceof Double && op1 instanceof Double) {
      // Special case if the 2 parameters are NaN value : we want 'NaN NaN ==' to be true
      // NaN is not convertible to BigDecimal, so we cannot use the compare method
      if (Double.isNaN((Double) op1) || Double.isNaN((Double) op2)) {
        stack.push(Double.isNaN((Double) op1) && Double.isNaN((Double) op2));
      } else {
        stack.push(0 == EQ.compare((Number) op1, (Number) op2));
      }
    } else if (op1 instanceof Double && Double.isNaN((Double) op1)) { // Do we have only one NaN ?
      stack.push(false);
    } else if (op2 instanceof Double && Double.isNaN((Double) op2)) { // Do we have only one NaN ?
      stack.push(false);
    } else if (op2 instanceof Number && op1 instanceof Number) {
      stack.push(0 == compare((Number) op1, (Number) op2));
    } else if (op1 instanceof Boolean || op1 instanceof String
        || op1 instanceof RealVector || op1 instanceof RealMatrix) {
      stack.push(op1.equals(op2));
    } else {
      throw new WarpScriptException(getName()
          + " can only operate on homogeneous numeric, string, boolean, vector or matrix types.");
    }

    return stack;
  }

  public static int compare(Number a, Number b) {
    if (a.equals(b)) {
      return 0;
    }

    if ((a instanceof Long || a instanceof Integer || a instanceof Short || a instanceof Byte)
        && (b instanceof Long || b instanceof Integer || b instanceof Short || b instanceof Byte)) {
      if (a.longValue() < b.longValue()) {
        return -1;
      } else if (a.longValue() > b.longValue()) {
        return 1;
      } else {
        return 0;
      }
    } else if (a instanceof Double && b instanceof Double) {
      return ((Double) a).compareTo((Double) b);
    } else {    
      // If the equals function fails and the types do not permit direct number comparison,
      // we test again with BigDecimal comparison for type abstraction
      // We want '10 10.0 ==' or '10 10.0 >=' to be true
      BigDecimal bda;
      BigDecimal bdb;
      
      if (a instanceof Double) {
        bda = new BigDecimal(((Number) a).doubleValue());
      } else if (a instanceof Float) {
        bda = new BigDecimal(((Number) a).floatValue());
      } else if (a instanceof Long || a instanceof Integer || a instanceof Short || a instanceof Byte) {
        bda = new BigDecimal(((Number) a).longValue());
      } else {
        bda = new BigDecimal(a.toString());
      }
      
      if (b instanceof Double) {
        bdb = new BigDecimal(((Number) b).doubleValue());
      } else if (a instanceof Float) {
        bdb = new BigDecimal(((Number) b).floatValue());
      } else if (b instanceof Long || b instanceof Integer || b instanceof Short || b instanceof Byte) {
        bdb = new BigDecimal(((Number) b).longValue());
      } else {
        bdb = new BigDecimal(b.toString());
      }

      return bda.compareTo(bdb);
    }
  }
}
