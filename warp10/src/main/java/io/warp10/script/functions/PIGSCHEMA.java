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

package io.warp10.script.functions;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.joda.time.DateTime;

import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

public class PIGSCHEMA extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public PIGSCHEMA(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    //
    // Loop at all stack elements, starting from the top
    //
    
    StringBuilder sb = new StringBuilder("(");
    
    for (int i = 0; i < stack.depth(); i++) {
      Object o = stack.get(i);
      
      String type = "bytearray";
      
      if (o instanceof Long) {
        type = "long";
      } else if (o instanceof Integer) {
        type = "int";
      } else if (o instanceof Double) {
        type = "double";
      } else if (o instanceof Float) {
        type = "float";
      } else if (o instanceof BigDecimal) {
        type = "bigdecimal";
      } else if (o instanceof BigInteger) {
        type = "biginteger";
      } else if (o instanceof String) {
        type = "chararray";
      } else if (o instanceof Boolean) {
        type = "boolean";
      } else if (o instanceof byte[]) {
        type = "bytearray";
      } else if (o instanceof Vector || o instanceof Set) {
        type = "bag{}";
      } else if (o instanceof List) {
        type = "tuple:()";
      } else if (o instanceof Map) {
        type = "map:[]";
      } else if (o instanceof GeoTimeSerie) {
        type = "bytearray";
      } else if (o instanceof DateTime) {
        type = "datetime";
      } else {
        type = "bytearray";
      }
      
      if (i > 0) {
        sb.append(", ");
      }
      
      if (0 == i) {
        sb.append("top");
      } else {
        sb.append("l");
        sb.append(i + 1);
      }
      sb.append(": ");
      sb.append(type);
    }
    
    sb.append(")");
    
    stack.push(sb.toString());
    
    return stack;
  }

}
