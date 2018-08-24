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

import io.warp10.continuum.gts.GTSEncoder;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Extract the labels of the GTS on top of the stack
 */
public class LABELS extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public LABELS(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object o = stack.pop();
    
    if (!(o instanceof GeoTimeSerie) && !(o instanceof GTSEncoder)) {
      throw new WarpScriptException(getName() + " expects a Geo Time Series or encoder instance on top of the stack.");
    }
    
    //
    // Clone the labels map
    //
    
    Map<String,String> labels = new HashMap<String,String>();
    if (o instanceof GeoTimeSerie) {
      labels.putAll(((GeoTimeSerie) o).getMetadata().getLabels());
    } else {
      labels.putAll(((GTSEncoder) o).getMetadata().getLabels());
    }
    stack.push(labels);

    return stack;
  }
}
