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

package io.warp10.standalone;

import io.warp10.WarpConfig;
import io.warp10.continuum.Configuration;
import io.warp10.continuum.MetadataUtils;
import io.warp10.continuum.ThrottlingManager;
import io.warp10.continuum.TimeSource;
import io.warp10.continuum.Tokens;
import io.warp10.continuum.WarpException;
import io.warp10.continuum.gts.GTSEncoder;
import io.warp10.continuum.gts.GTSHelper;
import io.warp10.continuum.ingress.DatalogForwarder;
import io.warp10.continuum.sensision.SensisionConstants;
import io.warp10.continuum.store.Constants;
import io.warp10.continuum.store.StoreClient;
import io.warp10.continuum.store.thrift.data.DatalogRequest;
import io.warp10.continuum.store.thrift.data.Metadata;
import io.warp10.crypto.CryptoUtils;
import io.warp10.crypto.KeyStore;
import io.warp10.crypto.OrderPreservingBase64;
import io.warp10.crypto.SipHashInline;
import io.warp10.quasar.token.thrift.data.WriteToken;
import io.warp10.script.WarpScriptException;
import io.warp10.sensision.Sensision;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.thrift.TDeserializer;
import org.apache.thrift.TException;
import org.apache.thrift.TSerializer;
import org.apache.thrift.protocol.TCompactProtocol;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;

public class StandaloneIngressHandler extends AbstractHandler {
  
  private static final Logger LOG = LoggerFactory.getLogger(StandaloneIngressHandler.class);
  
  /**
   * How many bytes do we buffer before flushing to persistent storage, this will also end up being the size of each archival chunk
   */
  public static final int ENCODER_SIZE_THRESHOLD = 500000;
  
  /**
   * How often (in number of ingested datapoints) to check for pushback
   */
  public static final long PUSHBACK_CHECK_INTERVAL = 1000L;
  
  /**
   * Default max size of value
   */
  public static final String DEFAULT_VALUE_MAXSIZE = "65536";
  
  private final KeyStore keyStore;
  private final StoreClient storeClient;
  private final StandaloneDirectoryClient directoryClient;
  
  private final byte[] classKey;
  private final byte[] labelsKey;  
  
  /**
   * Key to wrap the token in the file names
   */
  private final byte[] datalogPSK;

  private final long[] classKeyLongs;
  private final long[] labelsKeyLongs;
    
  private final File loggingDir;
  
  private final String datalogId;
  
  private final boolean logforwarded;
  
  private final DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss.SSS").withZoneUTC();
  
  private final long maxValueSize;
  
  public StandaloneIngressHandler(KeyStore keystore, StandaloneDirectoryClient directoryClient, StoreClient storeClient) {
    this.keyStore = keystore;
    this.storeClient = storeClient;
    this.directoryClient = directoryClient;
    
    this.classKey = this.keyStore.getKey(KeyStore.SIPHASH_CLASS);
    this.classKeyLongs = SipHashInline.getKey(this.classKey);
    
    this.labelsKey = this.keyStore.getKey(KeyStore.SIPHASH_LABELS);
    this.labelsKeyLongs = SipHashInline.getKey(this.labelsKey);
    
    Properties props = WarpConfig.getProperties();
    
    if (props.containsKey(Configuration.DATALOG_DIR)) {
      File dir = new File(props.getProperty(Configuration.DATALOG_DIR));
      
      if (!dir.exists()) {
        throw new RuntimeException("Data logging target '" + dir + "' does not exist.");
      } else if (!dir.isDirectory()) {
        throw new RuntimeException("Data logging target '" + dir + "' is not a directory.");
      } else {
        loggingDir = dir;
        LOG.info("Data logging enabled in directory '" + dir + "'.");
      }
      
      String id = props.getProperty(Configuration.DATALOG_ID);
      
      if (null == id) {
        throw new RuntimeException("Property '" + Configuration.DATALOG_ID + "' MUST be set to a unique value for this instance.");
      } else {
        datalogId = new String(OrderPreservingBase64.encode(id.getBytes(Charsets.UTF_8)), Charsets.US_ASCII);
      }
      
    } else {
      loggingDir = null;
      datalogId = null;
    }
    
    if (props.containsKey(Configuration.DATALOG_PSK)) {
      this.datalogPSK = this.keyStore.decodeKey(props.getProperty(Configuration.DATALOG_PSK));
    } else {
      this.datalogPSK = null;
    }
    
    this.logforwarded = "true".equals(props.getProperty(Configuration.DATALOG_LOGFORWARDED));
    
    this.maxValueSize = Long.parseLong(props.getProperty(Configuration.STANDALONE_VALUE_MAXSIZE, DEFAULT_VALUE_MAXSIZE));
  }
  
  @Override
  public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    
    String token = null;
    
    if (target.equals(Constants.API_ENDPOINT_UPDATE)) {
      baseRequest.setHandled(true);
    } else if (target.startsWith(Constants.API_ENDPOINT_UPDATE + "/")) {
      baseRequest.setHandled(true);
      token = target.substring(Constants.API_ENDPOINT_UPDATE.length() + 1);
    } else if (target.equals(Constants.API_ENDPOINT_META)) {
      handleMeta(target, baseRequest, request, response);
      return;
    } else {
      return;
    }    
    
    try {
      //
      // CORS header
      //
      
      response.setHeader("Access-Control-Allow-Origin", "*");
      
      long nano = System.nanoTime();
      
      //
      // Extract DatalogRequest if specified
      //
            
      String datalogHeader = request.getHeader(Constants.getHeader(Configuration.HTTP_HEADER_DATALOG));
      
      DatalogRequest dr = null;
      
      boolean forwarded = false;
      
      if (null != datalogHeader) {
        byte[] bytes = OrderPreservingBase64.decode(datalogHeader.getBytes(Charsets.US_ASCII));
        
        if (null != datalogPSK) {
          bytes = CryptoUtils.unwrap(datalogPSK, bytes);
        }
        
        if (null == bytes) {
          throw new IOException("Invalid Datalog header.");
        }
          
        TDeserializer deser = new TDeserializer(new TCompactProtocol.Factory());
          
        try {
          dr = new DatalogRequest();
          deser.deserialize(dr, bytes);
        } catch (TException te) {
          throw new IOException();
        }
        
        token = dr.getToken();
        
        Map<String,String> labels = new HashMap<String,String>();
        labels.put(SensisionConstants.SENSISION_LABEL_ID, new String(OrderPreservingBase64.decode(dr.getId().getBytes(Charsets.US_ASCII)), Charsets.UTF_8));
        labels.put(SensisionConstants.SENSISION_LABEL_TYPE, dr.getType());
        Sensision.update(SensisionConstants.CLASS_WARP_DATALOG_REQUESTS_RECEIVED, labels, 1);
        forwarded = true;
      }

      //
      // TODO(hbs): Extract producer/owner from token
      //
      
      if (null == token) {
        token = request.getHeader(Constants.getHeader(Configuration.HTTP_HEADER_TOKENX));
      }
              
      WriteToken writeToken;
      
      try {
        writeToken = Tokens.extractWriteToken(token);
      } catch (WarpScriptException ee) {
        throw new IOException(ee);
      }
      
      String application = writeToken.getAppName();
      String producer = Tokens.getUUID(writeToken.getProducerId());
      String owner = Tokens.getUUID(writeToken.getOwnerId());
            
      Map<String,String> sensisionLabels = new HashMap<String,String>();
      sensisionLabels.put(SensisionConstants.SENSISION_LABEL_PRODUCER, producer);

      long count = 0;
      long total = 0;
      
      File loggingFile = null;   
      PrintWriter loggingWriter = null;

      try {      
        if (null == producer || null == owner) {
          response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid token.");
          return;
        }
        
        //
        // Build extra labels
        //
        
        Map<String,String> extraLabels = new HashMap<String,String>();
        
        // Add labels from the WriteToken if they exist
        if (writeToken.getLabelsSize() > 0) {
          extraLabels.putAll(writeToken.getLabels());
        }
        
        // Force internal labels
        extraLabels.put(Constants.PRODUCER_LABEL, producer);
        extraLabels.put(Constants.OWNER_LABEL, owner);
        // FIXME(hbs): remove me, apps should be set in all tokens now...
        if (null != application) {
          extraLabels.put(Constants.APPLICATION_LABEL, application);
          sensisionLabels.put(SensisionConstants.SENSISION_LABEL_APPLICATION, application);
        } else {
          // remove application label
          extraLabels.remove(Constants.APPLICATION_LABEL);
        }
        
        //
        // Determine if content if gzipped
        //

        boolean gzipped = false;
            
        if (null != request.getHeader("Content-Type") && "application/gzip".equals(request.getHeader("Content-Type"))) {      
          gzipped = true;
        }
        
        BufferedReader br = null;
            
        if (gzipped) {
          GZIPInputStream is = new GZIPInputStream(request.getInputStream());
          br = new BufferedReader(new InputStreamReader(is));
        } else {    
          br = request.getReader();
        }

        //
        // Get the present time
        //
        
        Long now = TimeSource.getTime();

        //
        // Check the value of the 'now' header
        //
        // The following values are supported:
        //
        // A number, which will be interpreted as an absolute time reference,
        // i.e. a number of time units since the Epoch.
        //
        // A number prefixed by '+' or '-' which will be interpreted as a
        // delta from the present time.
        //
        // A '*' which will mean to not set 'now', and to recompute its value
        // each time it's needed.
        //
        
        String nowstr = null != dr ? dr.getNow() : request.getHeader(Constants.getHeader(Configuration.HTTP_HEADER_NOW_HEADERX));
        
        if (null != nowstr) {
          if ("*".equals(nowstr)) {
            now = null;
          } else if (nowstr.startsWith("+")) {
            try {
              long delta = Long.parseLong(nowstr.substring(1));
              now = now + delta;
            } catch (Exception e) {
              throw new IOException("Invalid base timestamp.");
            }                  
          } else if (nowstr.startsWith("-")) {
            try {
              long delta = Long.parseLong(nowstr.substring(1));
              now = now - delta;
            } catch (Exception e) {
              throw new IOException("Invalid base timestamp.");
            }                            
          } else {
            try {
              now = Long.parseLong(nowstr);
            } catch (Exception e) {
              throw new IOException("Invalid base timestamp.");
            }                  
          }
        }
        
        //
        // Open the logging file if logging is enabled
        //
        
        if (null != loggingDir) {
          long nanos = null != dr ? dr.getTimestamp() : TimeSource.getNanoTime();
          StringBuilder sb = new StringBuilder();
          sb.append(Long.toHexString(nanos));
          sb.insert(0, "0000000000000000", 0, 16 - sb.length());
          sb.append("-");
          if (null != dr) {
            sb.append(dr.getId());
          } else {
            sb.append(datalogId);
          }
          
          sb.append("-");
          sb.append(dtf.print(nanos / 1000000L));
          sb.append(Long.toString(1000000L + (nanos % 1000000L)).substring(1));
          sb.append("Z");
          
          if (null == dr) {
            dr = new DatalogRequest();
            dr.setTimestamp(nanos);
            dr.setType(Constants.DATALOG_UPDATE);
            dr.setId(datalogId);
            dr.setToken(token); 
            
            if (null == now) {
              //
              // We MUST force 'now', otherwise forwarded metrics will not have a
              // coherent time. This alters the semantics slightly but make it
              // coherent across the board.
              //
              now = TimeSource.getTime();
            }
            dr.setNow(Long.toString(now));
          }
          
          if (null != dr && (!forwarded || (forwarded && this.logforwarded))) {
            
            //
            // Serialize the request
            //
            
            TSerializer ser = new TSerializer(new TCompactProtocol.Factory());
            
            byte[] encoded;
            
            try {
              encoded = ser.serialize(dr);
            } catch (TException te) {
              throw new IOException(te);
            }
            
            if (null != this.datalogPSK) {
              encoded = CryptoUtils.wrap(this.datalogPSK, encoded);
            }
            
            encoded = OrderPreservingBase64.encode(encoded);
                    
            loggingFile = new File(loggingDir, sb.toString());
            
            loggingWriter = new PrintWriter(new FileWriterWithEncoding(loggingFile, Charsets.UTF_8));
            
            //
            // Write request
            //
            
            loggingWriter.println(new String(encoded, Charsets.US_ASCII));          
          }
          
          //
          // Force 'now'
          //
          
          now = Long.parseLong(dr.getNow());
        }
        

        //
        // Loop on all lines
        //
        
        GTSEncoder lastencoder = null;
        GTSEncoder encoder = null;
        
        //
        // Chunk index when archiving
        //
        
        do {
          
          String line = br.readLine();
          
          if (null == line) {
            break;
          }
          
          line = line.trim();
          
          if (0 == line.length()) {
            continue;
          }

          //
          // Ignore comments
          //
          
          if ('#' == line.charAt(0)) {
            continue;
          }
          
          //
          // Check for pushback
          // TODO(hbs): implement the actual push back if we are over the subscribed limit
          //
          
          if (count % PUSHBACK_CHECK_INTERVAL == 0) {
            Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_DATAPOINTS_RAW, sensisionLabels, count);
            total += count;
            count = 0;
          }
          
          count++;

          try {
            encoder = GTSHelper.parse(lastencoder, line, extraLabels, now, maxValueSize, false);
            //nano2 += System.nanoTime() - nano0;
          } catch (ParseException pe) {
            Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_PARSEERRORS, sensisionLabels, 1);            
            throw new IOException("Parse error at '" + line + "'", pe);
          }

          if (encoder != lastencoder || lastencoder.size() > ENCODER_SIZE_THRESHOLD) {
            
            //
            // Check throttling
            //
            
            if (null != lastencoder) {
              
              // 128BITS
              lastencoder.setClassId(GTSHelper.classId(classKeyLongs, lastencoder.getName()));
              lastencoder.setLabelsId(GTSHelper.labelsId(labelsKeyLongs, lastencoder.getMetadata().getLabels()));

              ThrottlingManager.checkMADS(lastencoder.getMetadata(), producer, owner, application, lastencoder.getClassId(), lastencoder.getLabelsId());
              ThrottlingManager.checkDDP(lastencoder.getMetadata(), producer, owner, application, (int) lastencoder.getCount());
            }
            
            //
            // Build metadata object to push
            //
            
            if (encoder != lastencoder) {
              Metadata metadata = new Metadata(encoder.getMetadata());
              metadata.setSource(Configuration.INGRESS_METADATA_SOURCE);
              //nano6 += System.nanoTime() - nano0;
              this.directoryClient.register(metadata);
              //nano5 += System.nanoTime() - nano0;
            }

            
            if (null != lastencoder) {
              this.storeClient.store(lastencoder);              
            }

            if (encoder != lastencoder) {
              lastencoder = encoder;
            } else {
              //lastencoder = null
              //
              // Allocate a new GTSEncoder and reuse Metadata so we can
              // correctly handle a continuation line if this is what occurs next
              //
              Metadata metadata = lastencoder.getMetadata();
              lastencoder = new GTSEncoder(0L);
              lastencoder.setMetadata(metadata);
            }
          }
          
          //
          // Write the line last, so we do not write lines which triggered exceptions
          //
          
          if (null != loggingWriter) {
            loggingWriter.println(line);
          }
        } while (true); 
        
        br.close();
        
        if (null != lastencoder && lastencoder.size() > 0) {
          // 128BITS
          lastencoder.setClassId(GTSHelper.classId(classKeyLongs, lastencoder.getName()));
          lastencoder.setLabelsId(GTSHelper.labelsId(labelsKeyLongs, lastencoder.getMetadata().getLabels()));
                  
          ThrottlingManager.checkMADS(lastencoder.getMetadata(), producer, owner, application, lastencoder.getClassId(), lastencoder.getLabelsId());
          ThrottlingManager.checkDDP(lastencoder.getMetadata(), producer, owner, application, (int) lastencoder.getCount());
          this.storeClient.store(lastencoder);
        }        
        
        //
        // TODO(hbs): should we update the count in Sensision periodically so you can't trick the throttling mechanism?
        //
      } catch (WarpException we) {
        throw new IOException(we);
      } finally {               
        this.storeClient.store(null);
        this.directoryClient.register(null);
        Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_DATAPOINTS_RAW, sensisionLabels, count);
        Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_REQUESTS, sensisionLabels, 1);
        Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_TIME_US, sensisionLabels, (System.nanoTime() - nano) / 1000);
              
        if (null != loggingWriter) {
          Map<String,String> labels = new HashMap<String,String>();
          labels.put(SensisionConstants.SENSISION_LABEL_ID, new String(OrderPreservingBase64.decode(dr.getId().getBytes(Charsets.US_ASCII)), Charsets.UTF_8));
          labels.put(SensisionConstants.SENSISION_LABEL_TYPE, dr.getType());
          Sensision.update(SensisionConstants.CLASS_WARP_DATALOG_REQUESTS_LOGGED, labels, 1);

          loggingWriter.close();
          loggingFile.renameTo(new File(loggingFile.getAbsolutePath() + DatalogForwarder.DATALOG_SUFFIX));
        }

        //
        // Update stats with CDN
        //
        
        String cdn = request.getHeader(Constants.OVH_CDN_GEO_HEADER);
        
        if (null != cdn) {
          sensisionLabels.put(SensisionConstants.SENSISION_LABEL_CDN, cdn);
          // Per CDN stat is updated at the end, so update with 'total' + 'count'
          Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_DATAPOINTS_RAW, sensisionLabels, count + total);
          Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_REQUESTS, sensisionLabels, 1);
          Sensision.update(SensisionConstants.SENSISION_CLASS_CONTINUUM_STANDALONE_UPDATE_TIME_US, sensisionLabels, (System.nanoTime() - nano) / 1000);                  
        }
      }

      response.setStatus(HttpServletResponse.SC_OK);      
    } catch (Exception e) {
      if (!response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        return;
      }
    }
  }
  
  /**
   * Handle Metadata updating
   */
  public void handleMeta(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    if (target.equals(Constants.API_ENDPOINT_META)) {
      baseRequest.setHandled(true);
    } else {
      return;
    }
    
    try {
      //
      // CORS header
      //
      
      response.setHeader("Access-Control-Allow-Origin", "*");

      //
      // Extract DatalogRequest if specified
      //
            
      String datalogHeader = request.getHeader(Constants.getHeader(Configuration.HTTP_HEADER_DATALOG));
      
      DatalogRequest dr = null;
      
      boolean forwarded = false;
      
      if (null != datalogHeader) {
        byte[] bytes = OrderPreservingBase64.decode(datalogHeader.getBytes(Charsets.US_ASCII));
        
        if (null != datalogPSK) {
          bytes = CryptoUtils.unwrap(datalogPSK, bytes);
        }
        
        if (null == bytes) {
          throw new IOException("Invalid Datalog header.");
        }
          
        TDeserializer deser = new TDeserializer(new TCompactProtocol.Factory());
          
        try {
          dr = new DatalogRequest();
          deser.deserialize(dr, bytes);
        } catch (TException te) {
          throw new IOException();
        }
        
        Map<String,String> labels = new HashMap<String,String>();
        labels.put(SensisionConstants.SENSISION_LABEL_ID, new String(OrderPreservingBase64.decode(dr.getId().getBytes(Charsets.US_ASCII)), Charsets.UTF_8));
        labels.put(SensisionConstants.SENSISION_LABEL_TYPE, dr.getType());
        Sensision.update(SensisionConstants.CLASS_WARP_DATALOG_REQUESTS_RECEIVED, labels, 1);
        
        forwarded = true;
      }

      //
      // Loop over the input lines.
      // Each has the following format:
      //
      // class{labels}{attributes}
      //
      
      String token = null != dr ? dr.getToken() : request.getHeader(Constants.getHeader(Configuration.HTTP_HEADER_TOKENX));
          
      WriteToken wtoken;
      
      try {
        wtoken = Tokens.extractWriteToken(token);
      } catch (WarpScriptException ee) {
        throw new IOException(ee);
      }

      String application = wtoken.getAppName();
      String producer = Tokens.getUUID(wtoken.getProducerId());
      String owner = Tokens.getUUID(wtoken.getOwnerId());

      if (null == producer || null == owner) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid token.");
        return;
      }
      
      //
      // Determine if content if gzipped
      //

      boolean gzipped = false;
          
      if (null != request.getHeader("Content-Type") && "application/gzip".equals(request.getHeader("Content-Type"))) {      
        gzipped = true;
      }
      
      BufferedReader br = null;
          
      if (gzipped) {
        GZIPInputStream is = new GZIPInputStream(request.getInputStream());
        br = new BufferedReader(new InputStreamReader(is));
      } else {    
        br = request.getReader();
      }

      File loggingFile = null;   
      PrintWriter loggingWriter = null;

      //
      // Open the logging file if logging is enabled
      //
      
      if (null != loggingDir) {
        long nanos = null != dr ? dr.getTimestamp() : TimeSource.getNanoTime();
        StringBuilder sb = new StringBuilder();
        sb.append(Long.toHexString(nanos));
        sb.insert(0, "0000000000000000", 0, 16 - sb.length());
        sb.append("-");
        if (null != dr) {
          sb.append(dr.getId());
        } else {
          sb.append(datalogId);
        }

        sb.append("-");
        sb.append(dtf.print(nanos / 1000000L));
        sb.append(Long.toString(1000000L + (nanos % 1000000L)).substring(1));
        sb.append("Z");
        
        if (null == dr) {
          dr = new DatalogRequest();
          dr.setTimestamp(nanos);
          dr.setType(Constants.DATALOG_META);
          dr.setId(datalogId);
          dr.setToken(token);          
        }
        
        if (null != dr && (!forwarded || (forwarded && this.logforwarded))) {        
          //
          // Serialize the request
          //
          
          TSerializer ser = new TSerializer(new TCompactProtocol.Factory());
          
          byte[] encoded;
          
          try {
            encoded = ser.serialize(dr);
          } catch (TException te) {
            throw new IOException(te);
          }
          
          if (null != this.datalogPSK) {
            encoded = CryptoUtils.wrap(this.datalogPSK, encoded);
          }
          
          encoded = OrderPreservingBase64.encode(encoded);
                  
          loggingFile = new File(loggingDir, sb.toString());
          loggingWriter = new PrintWriter(new FileWriterWithEncoding(loggingFile, Charsets.UTF_8));
          
          //
          // Write request
          //
          
          loggingWriter.println(new String(encoded, Charsets.US_ASCII));        
        }
      }

      try {
        //
        // Loop on all lines
        //

        while(true) {
          String line = br.readLine();
          
          if (null == line) {
            break;
          }
          
          // Ignore blank lines
          if ("".equals(line)) {
            continue;          
          }
          
          // Ignore comments
          if ('#' == line.charAt(0)) {
            continue;
          }
          
          Metadata metadata = MetadataUtils.parseMetadata(line);
          
          // Add labels from the WriteToken if they exist
          if (wtoken.getLabelsSize() > 0) {
            metadata.getLabels().putAll(wtoken.getLabels());
          }
          //
          // Force owner/producer
          //
          
          metadata.getLabels().put(Constants.PRODUCER_LABEL, producer);
          metadata.getLabels().put(Constants.OWNER_LABEL, owner);
          
          if (null != application) {
            metadata.getLabels().put(Constants.APPLICATION_LABEL, application);
          } else {
            // remove application label
            metadata.getLabels().remove(Constants.APPLICATION_LABEL);
          }

          if (!MetadataUtils.validateMetadata(metadata)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid metadata " + line);
            return;
          }
          
          metadata.setSource(Configuration.INGRESS_METADATA_UPDATE_ENDPOINT);
          this.directoryClient.register(metadata);
          
          //
          // Write the line last, so we do not write lines which triggered exceptions
          //
          
          if (null != loggingWriter) {
            loggingWriter.println(line);
          }
        }      
      } finally {
        if (null != loggingWriter) {
          Map<String,String> labels = new HashMap<String,String>();
          labels.put(SensisionConstants.SENSISION_LABEL_ID, new String(OrderPreservingBase64.decode(dr.getId().getBytes(Charsets.US_ASCII)), Charsets.UTF_8));
          labels.put(SensisionConstants.SENSISION_LABEL_TYPE, dr.getType());
          Sensision.update(SensisionConstants.CLASS_WARP_DATALOG_REQUESTS_LOGGED, labels, 1);

          loggingWriter.close();
          loggingFile.renameTo(new File(loggingFile.getAbsolutePath() + DatalogForwarder.DATALOG_SUFFIX));
        }
        this.directoryClient.register(null);
      }
      
      response.setStatus(HttpServletResponse.SC_OK);      
    } catch (Exception e) {
      if (!response.isCommitted()) {
        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        return;
      }
    }
  }
}
