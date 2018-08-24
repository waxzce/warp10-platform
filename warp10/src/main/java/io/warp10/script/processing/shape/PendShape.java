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

package io.warp10.script.processing.shape;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.processing.ProcessingUtil;

import java.util.List;

import processing.core.PGraphics;

/**
 * Call endShape
 */ 
public class PendShape extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public PendShape(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    
    List<Object> params = ProcessingUtil.parseParams(stack, 0, 1);
        
    PGraphics pg = (PGraphics) params.get(0);

    if (1 == params.size()) {
      pg.endShape();
    } else if (2 == params.size()) {
      String mode = params.get(1).toString();
      if ("OPEN".equals(mode)) {
        pg.endShape(PGraphics.OPEN);
      } else if ("CLOSE".equals(mode)) {
        pg.endShape(PGraphics.CLOSE);
      } else {
        throw new WarpScriptException(getName() + ": invalid mode, should be 'OPEN' or 'CLOSE'.");
      }
    }
    stack.push(pg);
        
    return stack;
  }
}
