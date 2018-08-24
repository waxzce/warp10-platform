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

package io.warp10.script.aggregator;

import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.StackUtils;
import io.warp10.script.WarpScriptAggregatorFunction;
import io.warp10.script.WarpScriptBucketizerFunction;
import io.warp10.script.WarpScriptMapperFunction;
import io.warp10.script.WarpScriptReducerFunction;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;

import java.util.Comparator;
import java.util.Arrays;

import com.geoxp.GeoXPLib;

/**
 * Return the Nth percentile of the values on the interval.
 * The returned location will be that of the chosen value
 * The returned elevation will be that of the chosen value
 */
public class Percentile extends NamedWarpScriptFunction implements WarpScriptAggregatorFunction, WarpScriptMapperFunction, WarpScriptBucketizerFunction, WarpScriptReducerFunction {
  
  /**
   * Should we use linear interpolation?
   */
  final boolean interpolate;
  
  final double percentile;
  
  public static class Builder extends NamedWarpScriptFunction implements WarpScriptStackFunction {
    
    public Builder(String name) {
      super(name);
    }
    
    @Override
    public Object apply(WarpScriptStack stack) throws WarpScriptException {
      Object value = stack.pop();
      
      if (!(value instanceof Double)) {
        throw new WarpScriptException("Invalid parameter for " + getName());
      }
      
      double percentile = ((Number) value).doubleValue();
      
      if (percentile < 0.0D || percentile > 100.0D) {
        throw new WarpScriptException("Invalid percentile, MUST be between 0 and 100.");
      }
      
      stack.push(new Percentile(getName(), percentile, false));
      return stack;
    }
  }

  public Percentile(String name, double percentile, boolean interpolate) {
    super(name);
    this.percentile = percentile < 0.0D ? 0.0D : (percentile > 100.0D ? 100.0D : percentile);
    this.interpolate = interpolate;
  }
  
  @Override
  public Object apply(Object[] args) throws WarpScriptException {
    long[] ticks = (long[]) args[3];
    long[] locations = (long[]) args[4];
    long[] elevations = (long[]) args[5];
    final Object[] values = (Object[]) args[6];
    
    if (0 == ticks.length) {
      return new Object[] { Long.MAX_VALUE, GeoTimeSerie.NO_LOCATION, GeoTimeSerie.NO_ELEVATION, null };
    }
    
    //
    // Sort the array indices by increasing value
    // FIXME(hbs): find something less memory hungry...
    //
    
    Integer[] indices = new Integer[values.length];
    for (int i = 0; i < indices.length; i++) {
      indices[i] = i;
    }
    
    try {
      Arrays.sort(indices, new Comparator<Integer>() {
        @Override
        public int compare(Integer idx1, Integer idx2) {
          if (values[idx1] instanceof Double) {
            return (int) (((Number) values[idx1]).doubleValue() - ((Number) values[idx2]).doubleValue());
          } else if (values[idx1] instanceof Long) {
            return (int) (((Number) values[idx1]).longValue() - ((Number) values[idx2]).longValue());
          } else {
            throw new RuntimeException("PERCENTILE can only operate on numeric Geo Time Series.");
          }
        }
      });      
    } catch (RuntimeException re) {
      throw new WarpScriptException(re);
    }
    
    //
    // Compute rank
    //
    
    int n = (int) Math.round(0.5 + this.percentile * indices.length / 100.0) - 1;

    if (!this.interpolate) {
      if (n >= indices.length) { n--; }
      return new Object[] { ticks[indices[n]], locations[indices[n]], elevations[indices[n]], values[indices[n]] };
    } else {
      int m = (int) Math.floor(0.5 + this.percentile * indices.length / 100.0) - 1;
      
      double pn = (100.0 / indices.length) * (n + 1 - 0.5D);
      double pm = (100.0 / indices.length) * (m + 1 - 0.5D);

      if (0 == n && this.percentile < pn) {
        return new Object[] { ticks[indices[0]], locations[indices[0]], elevations[indices[0]], values[indices[0]] };
      } else if (m == indices.length - 1 && this.percentile > pm) {
        return new Object[] { ticks[indices[m]], locations[indices[m]], elevations[indices[m]], values[indices[m]] };
      } else if (pn == this.percentile) {
        return new Object[] { ticks[indices[n]], locations[indices[n]], elevations[indices[n]], values[indices[n]] };
      } else if (pm == this.percentile) {
        return new Object[] { ticks[indices[m]], locations[indices[m]], elevations[indices[m]], values[indices[m]] };
      } else if (pm < this.percentile && this.percentile < pn) {
        double factor = indices.length * (this.percentile - pm) / 100.0D;
        
        long tick = (long) (ticks[indices[m]] + factor * (ticks[indices[n]] - ticks[indices[m]]));
        double v;
        
        if (values[indices[m]] instanceof Long) {
          v = ((Number) values[indices[m]]).longValue() + factor * (((Number) values[indices[n]]).longValue() - ((Number) values[indices[m]]).longValue());
        } else {
          v = ((Number) values[indices[m]]).doubleValue() + factor * (((Number) values[indices[n]]).doubleValue() - ((Number) values[indices[m]]).doubleValue());          
        }
        
        long location = GeoTimeSerie.NO_LOCATION;
        
        if (GeoTimeSerie.NO_LOCATION != locations[indices[m]] && GeoTimeSerie.NO_LOCATION != locations[indices[n]]) {
          double[] latlonm = GeoXPLib.fromGeoXPPoint(locations[indices[m]]);
          double[] latlonn = GeoXPLib.fromGeoXPPoint(locations[indices[n]]);
          
          double lat = latlonm[0] + factor * (latlonn[0] - latlonm[0]);
          double lon = latlonm[1] + factor * (latlonn[1] - latlonm[1]);
          
          location = GeoXPLib.toGeoXPPoint(lat, lon);
        }
        
        long elevation = GeoTimeSerie.NO_ELEVATION;
        
        if (GeoTimeSerie.NO_LOCATION != elevations[indices[m]] && GeoTimeSerie.NO_ELEVATION != elevations[indices[n]]) {
          elevation = (long) (elevations[indices[m]] + factor * (elevations[indices[n]] - elevations[indices[m]]));
        }
        
        return new Object[] { tick, location, elevation, v };
      } else {
        throw new WarpScriptException("Twilight zone!");
      }
    }
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(StackUtils.toString(this.percentile));
    sb.append(" ");
    sb.append(this.getName());
    return sb.toString();
  }
}
