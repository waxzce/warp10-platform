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

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.aggregator.FirstGT;

public class MAPPERGT extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public MAPPERGT(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object o = stack.pop();
    
    if (o instanceof Long) {
      stack.push(new FirstGT(getName(), (long) o));
    } else if (o instanceof Double) {
      stack.push(new FirstGT(getName(), (double) o));
    } else if (o instanceof String) {
      stack.push(new FirstGT(getName(), (String) o));
    } else {
      throw new WarpScriptException(getName() + " expects a LONG, DOUBLE or STRING value on top of the stack.");
    }
    
    return stack;
  }
}
