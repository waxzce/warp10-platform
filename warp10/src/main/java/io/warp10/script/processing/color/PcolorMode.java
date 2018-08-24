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

package io.warp10.script.processing.color;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.processing.ProcessingUtil;

import java.util.List;

import processing.core.PGraphics;
import processing.core.PImage;

/**
 * Call background
 */
public class PcolorMode extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public PcolorMode(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    
    List<Object> params = ProcessingUtil.parseParams(stack, 1, 2, 4, 5);
        
    PGraphics pg = (PGraphics) params.get(0);
    
    String modestr = params.get(1).toString();
    
    if (!"RGB".equals(modestr) && !"HSB".equals(modestr)) {
      throw new WarpScriptException(getName() + ": invalid mode, should be one of 'RGB' or 'HSB'");      
    }
    
    int mode = "RGB".equals(modestr) ? PGraphics.RGB : PGraphics.HSB;
    
    if (2 == params.size()) {
      pg.colorMode(mode);
    } else if (3 == params.size()) {
      pg.colorMode(
        mode,
        ((Number) params.get(2)).floatValue()
      );                
    } else if (5 == params.size()) {
      pg.colorMode(
        mode,
        ((Number) params.get(2)).floatValue(),
        ((Number) params.get(3)).floatValue(),
        ((Number) params.get(4)).floatValue()
      );          
    } else if (6 == params.size()) {
      pg.colorMode(
        mode,
        ((Number) params.get(2)).floatValue(),
        ((Number) params.get(3)).floatValue(),
        ((Number) params.get(4)).floatValue(),
        ((Number) params.get(5)).floatValue()
      );          
    }
    
    stack.push(pg);
        
    return stack;
  }
}
