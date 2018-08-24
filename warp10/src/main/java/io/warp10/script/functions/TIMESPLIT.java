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

import io.warp10.continuum.gts.GTSHelper;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.GTSStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Apply timesplit on GTS instances
 *
 * TIMESPLIT expects the following parameters on the stack:
 *
 * 3: quiet A quiet period in microsecondes
 * 2: minValues A minimum number of values
 * 1: label The name of the label which will hold the sequence
 */
public class TIMESPLIT extends GTSStackFunction {

  private static final String QUIETPERIOD = "quietPeriod";
  private static final String MINVALUES = "minValues";
  private static final String LABEL = "label";

  public TIMESPLIT(String name) {
    super(name);
  }

  @Override
  protected Map<String, Object> retrieveParameters(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();


    if (!(top instanceof String)) {
      throw new WarpScriptException(getName() + " expects a label on top of the stack.");
    }

    Map<String,Object> params = new HashMap<String, Object>();

    params.put(LABEL, (String) top);

    top = stack.pop();

    if (!(top instanceof Long)) {
      throw new WarpScriptException(getName() + " expects a minimum number of values under the label.");
    }

    params.put(MINVALUES, (long) top);

    top = stack.pop();

    if (!(top instanceof Long)) {
      throw new WarpScriptException(getName() + " expects a quiet period under the minimum number of values.");
    }

    params.put(QUIETPERIOD, (long) top);

    return params;
  }

  @Override
  protected Object gtsOp(Map<String, Object> params, GeoTimeSerie gts) throws WarpScriptException {
    long quietPeriod = (long) params.get(QUIETPERIOD);
    int minvalues = ((Number) params.get(MINVALUES)).intValue();
    String label = (String) params.get(LABEL);

    List<GeoTimeSerie> result = GTSHelper.timesplit(gts, quietPeriod, minvalues, label);

    return result;
  }
}
