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

package io.warp10.script.mapper;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

public abstract class MapperKernel extends NamedWarpScriptFunction implements WarpScriptStackFunction {    
  public MapperKernel(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();
    
    if (!(top instanceof Long)) {
      throw new WarpScriptException(getName() + " expects a window width in ticks on top of the stack.");
    }
    
    int width = ((Number) top).intValue();
    
    if (0 == width % 2) {
      throw new WarpScriptException(getName() + " window width MUST be odd (we count ticks, not intervals).");
    }
    
    top = stack.pop();
    
    if (!(top instanceof Long)) {
      throw new WarpScriptException(getName() + " expects a step length as a LONG.");
    }
    
    long step = ((Number) top).longValue();
    
    MapperKernelSmoother smoother = new MapperKernelSmoother(getName(), step, width, getWeights(step, width));
    
    stack.push(smoother);
    
    return stack;
  }

  abstract double[] getWeights(long step, int width);
}
