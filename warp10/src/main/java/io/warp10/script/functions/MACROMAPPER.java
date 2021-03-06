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

import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptAggregatorFunction;
import io.warp10.script.WarpScriptBucketizerFunction;
import io.warp10.script.WarpScriptMapperFunction;
import io.warp10.script.WarpScriptReducerFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStack.Macro;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.geoxp.GeoXPLib;

public class MACROMAPPER extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public static class MacroMapperWrapper extends NamedWarpScriptFunction implements WarpScriptMapperFunction, WarpScriptReducerFunction, WarpScriptBucketizerFunction, WarpScriptAggregatorFunction {

    private TOLIST tolist = new TOLIST("");
    
    private final WarpScriptStack stack;
    private final Macro macro;
    
    public MacroMapperWrapper(String name, WarpScriptStack stack, Macro macro) {
      super(name);
      this.stack = stack;
      this.macro = macro;
    }
    
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(WarpScriptStack.MACRO_START);
      sb.append(" ");
      sb.append(macro.toString());
      sb.append(" ");
      sb.append(getName());
      sb.append(" ");
      sb.append(WarpScriptStack.MACRO_END);
      sb.append(" ");
      sb.append(WarpScriptLib.EVAL);
      return sb.toString();
    }
    
    @Override
    public Object apply(Object[] args) throws WarpScriptException {
      //
      // Push arguments onto the stack
      //
      
      long tick = (long) args[0];
      String[] names = (String[]) args[1];
      Map<String,String>[] labels = (Map<String,String>[]) args[2];
      long[] ticks = (long[]) args[3];
      long[] locations = (long[]) args[4];
      long[] elevations = (long[]) args[5];
      Object[] values = (Object[]) args[6];
      
      stack.push(tick);
  
      List<String> lnames = new ArrayList<String>();      
      for (String name: names) {
        lnames.add(name);
      }      
      stack.push(lnames);

      List<Map<String,String>> llabels = new ArrayList<Map<String,String>>();      
      for (Map<String,String> label: labels) {
        llabels.add(label);
      }
      stack.push(llabels);
      
      ArrayList<Long> lticks = new ArrayList<Long>();
      for (long l: ticks) {
        lticks.add(l);
      }
      stack.push(lticks);
      
      ArrayList<Double> lats = new ArrayList<Double>();
      ArrayList<Double> lons = new ArrayList<Double>();
      for (long l: locations) {
        if (GeoTimeSerie.NO_LOCATION == l) {
          lats.add(Double.NaN);
          lons.add(Double.NaN);
        } else {
          double[] latlon = GeoXPLib.fromGeoXPPoint(l);
          lats.add(latlon[0]);
          lons.add(latlon[1]);
        }
      }
      stack.push(lats);
      stack.push(lons);

      ArrayList<Object> elevs = new ArrayList<Object>();
      for (long l: elevations) {
        if (GeoTimeSerie.NO_ELEVATION == l) {
          elevs.add(Double.NaN);
        } else {
          elevs.add(l);
        }
      }
      stack.push(elevs);

      ArrayList<Object> lvalues = new ArrayList<Object>();      
      for (Object value: values) {
        lvalues.add(value);
      }
      stack.push(lvalues);
      
      stack.push(8);
      tolist.apply(stack);

      //
      // Execute macro
      //
      
      stack.exec(this.macro);
      
      //
      // Check type of result
      //
      
      Object res = stack.peek();
      
      if (res instanceof List) {
        stack.drop();
        Object[] ores = ((List) res).toArray();
        Object[] ores2 = new Object[4];
        
        ores2[0] = ores[0];
        if (Double.isNaN((double) ores[1]) || Double.isNaN((double) ores[2])) {
          ores2[1] = GeoTimeSerie.NO_LOCATION;
        } else {
          ores2[1] = GeoXPLib.toGeoXPPoint((double) ores[1], (double) ores[2]);
        }
        if (Double.isNaN((double) ores[3])) {
          ores2[2] = GeoTimeSerie.NO_ELEVATION;
        } else {
          ores2[2] = (long) ores[3];
        }
        ores2[3] = ores[4];
        
        return ores2;
      } else if (res instanceof Map) {
        stack.drop();
        
        Set<Object> keys = ((Map) res).keySet();
        
        for (Object key: keys) {
          Object[] ores = (Object[]) ((List) ((Map) res).get(key)).toArray();
          
          Object[] ores2 = new Object[4];
          
          ores2[0] = ores[0];
          if (Double.isNaN((double) ores[1]) || Double.isNaN((double) ores[2])) {
            ores2[1] = GeoTimeSerie.NO_LOCATION;
          } else {
            ores2[1] = GeoXPLib.toGeoXPPoint((double) ores[1], (double) ores[2]);
          }
          if (Double.isNaN((double) ores[3])) {
            ores2[2] = GeoTimeSerie.NO_ELEVATION;
          } else {
            ores2[2] = (long) ores[3];
          }
          ores2[3] = ores[4];
          
          ((Map) res).put(key, ores2);
        }

        return res;
      } else {
        //
        // Retrieve result
        //
        
        Object value = stack.pop();
        Object elevation = stack.pop();
        Object longitude = stack.pop();
        Object latitude = stack.pop();
        Object rtick = stack.pop();
        
        long location = GeoTimeSerie.NO_LOCATION;
        
        if (!(longitude instanceof Double) || !(latitude instanceof Double)) {
          throw new WarpScriptException("Macro MUST return a latitude and a longitude which are of type DOUBLE.");
        } else {
          if (!Double.isNaN((double) latitude) && !Double.isNaN((double) longitude)) {
            location = GeoXPLib.toGeoXPPoint((double) latitude, (double) longitude);
          }
        }
        
        long elev = GeoTimeSerie.NO_ELEVATION;
              
        if (!(elevation instanceof Double) && !(elevation instanceof Long)) {
          throw new WarpScriptException("Macro MUST return an elevation which is either NaN or LONG.");
        } else {
          if (elevation instanceof Long) {
            elev = (long) elevation;
          } else if (!Double.isNaN((double) elevation)) {
            throw new WarpScriptException("Macro MUST return an elevations which is NaN if it is of type DOUBLE.");
          }
        }
        
        if (!(rtick instanceof Long)) {
          throw new WarpScriptException("Macro MUST return a tick of type LONG.");
        }
        
        return new Object[] { (long) rtick, location, elev, value };        
      }
    }
  }
  
  public MACROMAPPER(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();
    
    if (!(top instanceof Macro)) {
      throw new WarpScriptException(getName() + " expects a macro on top of the stack.");
    }
    
    stack.push(new MacroMapperWrapper(getName(), stack, (Macro) top));
    
    return stack;
  }

}
