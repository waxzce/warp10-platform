package io.warp10.hadoop;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hadoop.io.ArrayPrimitiveWritable;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.EnumSetWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.GenericWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.ObjectWritable;
import org.apache.hadoop.io.ShortWritable;
import org.apache.hadoop.io.SortedMapWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.VIntWritable;
import org.apache.hadoop.io.VLongWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.lib.join.TupleWritable;

/**
 * Utility class to convert to/from Writables
 */
public class WritableUtils {
  public static Object fromWritable(Writable w) throws IOException {
    if (w instanceof Text) {
      return ((Text) w).toString();
    } else if (w instanceof BytesWritable) {
      return ((BytesWritable) w).copyBytes();
    } else if (w instanceof NullWritable) {
      return null;
    } else if (w instanceof LongWritable) {
      return ((LongWritable) w).get();
    } else if (w instanceof IntWritable) {
      return (long) ((IntWritable) w).get();
    } else if (w instanceof ByteWritable) {
      return (long) ((ByteWritable) w).get();
    } else if (w instanceof ShortWritable) {
      return (long) ((ShortWritable) w).get();
    } else if (w instanceof ArrayWritable) {
      Writable[] a = ((ArrayWritable) w).get();
      List<Object> l = new ArrayList<Object>();
      for (Writable ww: a) {
        l.add(fromWritable(ww));
      }
      return l;
    } else if (w instanceof BooleanWritable) {
      return ((BooleanWritable) w).get();
    } else if (w instanceof DoubleWritable) {
      return ((DoubleWritable) w).get();
    } else if (w instanceof FloatWritable) {
      return (float) ((FloatWritable) w).get();
    } else if (w instanceof MapWritable) {
      Map<Object,Object> map = new HashMap<Object,Object>();
      for (Entry<Writable,Writable> entry: ((MapWritable) w).entrySet()) {
        map.put(fromWritable(entry.getKey()), fromWritable(entry.getValue()));
      }
      return map;
    } else if (w instanceof GenericWritable) {
      return fromWritable(((GenericWritable) w).get());
    } else if (w instanceof SortedMapWritable) {
      Map<Object,Object> map = new LinkedHashMap<Object,Object>();
      for (Entry<WritableComparable,Writable> entry: ((SortedMapWritable) w).entrySet()) {
        map.put(fromWritable(entry.getKey()), fromWritable(entry.getValue()));        
      }
      return map;
    } else if (w instanceof VIntWritable) {
      return (long) ((VIntWritable) w).get();
    } else if (w instanceof VLongWritable) {
      return ((VLongWritable) w).get();
    } else {
      throw new IOException("Unsupported Writable implementation " + w.getClass());
    }  
  }
  
  public static Writable toWritable(Object o) throws IOException {
    if (o instanceof Long) {
      return new LongWritable(((Long) o).longValue());
    } else if (o instanceof String) {
      return new Text(o.toString());
    } else if (o instanceof byte[]) {
      return new BytesWritable((byte[]) o);
    } else if (o instanceof Integer) {
      return new IntWritable(((Integer) o).intValue());
    } else if (o instanceof Short) {
      return new ShortWritable(((Short) o).shortValue());
    } else if (o instanceof Byte) {
      return new ByteWritable(((Byte) o).byteValue());
    } else if (o instanceof Double) {
      return new DoubleWritable(((Double) o).doubleValue());
    } else if (o instanceof Float) {
      return new FloatWritable(((Float) o).floatValue());
    } else if (o instanceof Boolean) {
      return new BooleanWritable(((Boolean) o).booleanValue());
    } else if (o instanceof List) {
      Writable[] a = new Writable[((List) o).size()];
      for (int i = 0; i < a.length; i++) {
        a[i] = new ObjectWritable(toWritable(((List) o).get(i)));
      }
      return new ArrayWritable(ObjectWritable.class, a);
    } else if (o instanceof Map) {
      MapWritable map = new MapWritable();
      for (Entry<Object,Object> entry: ((Map<Object,Object>) o).entrySet()) {
        map.put(toWritable(entry.getKey()), toWritable(entry.getValue()));
      }
      return map;
    } else if (null == o) {
      return NullWritable.get();
    } else {
      throw new IOException("Unsupported type " + o.getClass());
    }
  }
}
