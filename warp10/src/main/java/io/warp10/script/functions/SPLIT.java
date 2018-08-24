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

import io.warp10.continuum.gts.UnsafeString;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Split a String in segments given a delimiter
 */
public class SPLIT extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public SPLIT(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object o = stack.pop();
    
    if (!(o instanceof String)) {
      throw new WarpScriptException(getName() + " expects a string delimiter of length 1.");
    }
    
    if (1 != o.toString().length()) {
      throw new WarpScriptException(getName() + " expects a string delimiter of length 1.");
    }

    char delimiter = o.toString().charAt(0);
    
    o = stack.pop();

    if (!(o instanceof String)) {
      throw new WarpScriptException(getName() + " operates on a String.");
    }


    String[] tokens = UnsafeString.split(o.toString(), delimiter);
    
    List<String> ltokens = new ArrayList<String>();
    
    for (String token: tokens) {
      ltokens.add(token);
    }
    
    stack.push(ltokens);
    
    return stack;
  }
}
