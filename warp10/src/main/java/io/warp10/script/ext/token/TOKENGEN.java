//
//   Copyright 2018  Cityzen Data
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

package io.warp10.script.ext.token;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.thrift.TBase;
import org.apache.thrift.TException;

import io.warp10.continuum.store.Constants;
import io.warp10.crypto.KeyStore;
import io.warp10.quasar.encoder.QuasarTokenEncoder;
import io.warp10.quasar.token.thrift.data.ReadToken;
import io.warp10.quasar.token.thrift.data.TokenType;
import io.warp10.quasar.token.thrift.data.WriteToken;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

public class TOKENGEN extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  private final QuasarTokenEncoder encoder = new QuasarTokenEncoder();

  public static final String KEY_TOKEN = "token";
  public static final String KEY_IDENT = "ident";

  public static final String KEY_ID = "id";
  
  public static final String KEY_TYPE = "type";
  public static final String KEY_APPLICATION = "application";
  public static final String KEY_EXPIRY = "expiry";
  public static final String KEY_ISSUANCE = "issuance";
  public static final String KEY_TTL = "ttl";
  public static final String KEY_LABELS = "labels";
  public static final String KEY_ATTRIBUTES = "attributes";
  public static final String KEY_OWNERS = "owners";
  public static final String KEY_OWNER = "owner";
  public static final String KEY_PRODUCERS = "producers";
  public static final String KEY_PRODUCER = "producer";
  public static final String KEY_APPLICATIONS = "applications";

  private long DEFAULT_TTL = 0;
  
  private byte[] tokenAESKey = null;
  private byte[] tokenSipHashKey = null;
  
  public TOKENGEN(String name) {
    super(name);
  }
  
  public TOKENGEN(String name, KeyStore keystore) {
    super(name);
    tokenAESKey = keystore.getKey(KeyStore.AES_TOKEN);
    tokenSipHashKey = keystore.getKey(KeyStore.SIPHASH_TOKEN);
  }
  
  public TOKENGEN(String name, long ttl) {
    super(name);
    this.DEFAULT_TTL = ttl;
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    
    if (null == tokenAESKey || null == tokenSipHashKey) {
      throw new WarpScriptException(getName() + " cannot be used in this context.");
    }
    
    Object top = stack.pop();
    
    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + " expects a map on top of the stack.");
    }

    Map<Object,Object> params = (Map<Object,Object>) top;

    try {      
      TBase token = tokenFromMap(params);
      
      String tokenstr = encoder.encryptToken(token, this.tokenAESKey, this.tokenSipHashKey);
      String ident = encoder.getTokenIdent(tokenstr, this.tokenSipHashKey);
        
      Map<Object,Object> result = new HashMap<Object,Object>();
      result.put(KEY_TOKEN, tokenstr);
      result.put(KEY_IDENT, ident);
      if (null != params.get(KEY_ID)) {
        result.put(KEY_ID, params.get(KEY_ID));
      }
      stack.push(result);      
    } catch (TException te) {
      te.printStackTrace();
      throw new WarpScriptException("Error while generating token.", te);
    }
        
    return stack;
  }
  
  public TBase tokenFromMap(Map params) throws WarpScriptException {
    TBase token = null;

    if (TokenType.READ.toString().equals(params.get(KEY_TYPE))) {      
      ReadToken rtoken = new ReadToken();
      rtoken.setTokenType(TokenType.READ);

      if (null != params.get(KEY_OWNER)) {
        rtoken.setBilledId(encoder.toByteBuffer(params.get(KEY_OWNER).toString()));
      } else {
        throw new WarpScriptException(getName() + " missing '" + KEY_OWNER + "'.");
      }

      if (null == params.get(KEY_APPLICATION)) {
        throw new WarpScriptException(getName() + " missing '" + KEY_APPLICATION + "'.");
      }
      rtoken.setAppName(params.get(KEY_APPLICATION).toString());
      
      if (null != params.get(KEY_ISSUANCE)) {
        rtoken.setIssuanceTimestamp(((Number) params.get(KEY_ISSUANCE)).longValue() / Constants.TIME_UNITS_PER_MS);
      } else {
        rtoken.setIssuanceTimestamp(System.currentTimeMillis());
      }
      
      if (null != params.get(KEY_TTL)) {
        long ttl = ((Number) params.get(KEY_TTL)).longValue();
        ttl = ttl / Constants.TIME_UNITS_PER_MS;
        if (ttl > Long.MAX_VALUE - rtoken.getIssuanceTimestamp()) { 
          rtoken.setExpiryTimestamp(Long.MAX_VALUE);
        } else {
          rtoken.setExpiryTimestamp(rtoken.getIssuanceTimestamp() + ttl);
        }
      } else if (null != params.get(KEY_EXPIRY)) {
        rtoken.setExpiryTimestamp(((Number) params.get(KEY_EXPIRY)).longValue() / Constants.TIME_UNITS_PER_MS);
      } else if (0 == DEFAULT_TTL) {
        throw new WarpScriptException(getName() + " missing '" + KEY_TTL + "' or '" + KEY_EXPIRY + "'.");
      } else {
        rtoken.setExpiryTimestamp(rtoken.getIssuanceTimestamp() + DEFAULT_TTL);
      }

      if (null != params.get(KEY_OWNERS)) {
        if (!(params.get(KEY_OWNERS) instanceof List)) {
          throw new WarpScriptException(getName() + " expects '" + KEY_OWNERS + "' to be a list of UUIDs.");
        }
        for (Object uuid: (List) params.get(KEY_OWNERS)) {
          rtoken.addToOwners(encoder.toByteBuffer(uuid.toString()));
        }
        if (0 == rtoken.getOwnersSize()) {
          rtoken.setOwners(new ArrayList<ByteBuffer>());
        }
      } else {
        rtoken.setOwners(new ArrayList<ByteBuffer>());
      }
      
      if (null != params.get(KEY_PRODUCERS)) {
        if (!(params.get(KEY_PRODUCERS) instanceof List)) {
          throw new WarpScriptException(getName() + " expects '" + KEY_PRODUCERS + "' to be a list of UUIDs.");
        }
        for (Object uuid: (List) params.get(KEY_PRODUCERS)) {
          rtoken.addToProducers(encoder.toByteBuffer(uuid.toString()));
        }        
        if (0 == rtoken.getProducersSize()) {
          rtoken.setProducers(new ArrayList<ByteBuffer>());          
        }
      } else {
        rtoken.setProducers(new ArrayList<ByteBuffer>());
      }
      
      if (null != params.get(KEY_APPLICATIONS)) {
        if (!(params.get(KEY_APPLICATIONS) instanceof List)) {
          throw new WarpScriptException(getName() + " expects '" + KEY_APPLICATIONS + "' to be a list of application names.");
        }
        for (Object app: (List) params.get(KEY_APPLICATIONS)) {
          rtoken.addToApps(app.toString());
        }
        if (0 == rtoken.getAppsSize()) {
          rtoken.setApps(new ArrayList<String>());          
        }
      } else {
        rtoken.setApps(new ArrayList<String>());
      }
      
      if (null != params.get(KEY_ATTRIBUTES)) {
        if (!(params.get(KEY_ATTRIBUTES) instanceof Map)) {
          throw new WarpScriptException(getName() + " expects '" + KEY_ATTRIBUTES + "' to be a map.");
        }
        for (Entry<Object,Object> entry: ((Map<Object,Object>) params.get(KEY_ATTRIBUTES)).entrySet()) {
          if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects '" + KEY_ATTRIBUTES + "' to be a map of STRING keys and values.");
          }
          rtoken.putToAttributes(entry.getKey().toString(), entry.getValue().toString());
        }
      }

      if (null != params.get(KEY_LABELS)) {
        if (!(params.get(KEY_LABELS) instanceof Map)) {
          throw new WarpScriptException(getName() + " expects '" + KEY_LABELS + "' to be a map.");
        }
        for (Entry<Object,Object> entry: ((Map<Object,Object>) params.get(KEY_LABELS)).entrySet()) {
          if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects '" + KEY_LABELS + "' to be a map of STRING keys and values.");
          }
          rtoken.putToLabels(entry.getKey().toString(), entry.getValue().toString());
        }
      }
      
      token = rtoken;        
    } else if (TokenType.WRITE.toString().equals(params.get(KEY_TYPE))) {
      WriteToken wtoken = new WriteToken();
      wtoken.setTokenType(TokenType.WRITE);
      
      if (null == params.get(KEY_APPLICATION)) {
        throw new WarpScriptException(getName() + " missing '" + KEY_APPLICATION + "'.");
      }
      wtoken.setAppName(params.get(KEY_APPLICATION).toString());
      
      if (null != params.get(KEY_ISSUANCE)) {
        wtoken.setIssuanceTimestamp(((Number) params.get(KEY_ISSUANCE)).longValue() / Constants.TIME_UNITS_PER_MS);
      } else {
        wtoken.setIssuanceTimestamp(System.currentTimeMillis());
      }
      
      if (null != params.get(KEY_TTL)) {
        long ttl = ((Number) params.get(KEY_TTL)).longValue();
        ttl = ttl / Constants.TIME_UNITS_PER_MS;
        if (ttl > Long.MAX_VALUE - wtoken.getIssuanceTimestamp()) { 
          wtoken.setExpiryTimestamp(Long.MAX_VALUE);
        } else {
          wtoken.setExpiryTimestamp(wtoken.getIssuanceTimestamp() + ttl);
        }
      } else if (null != params.get(KEY_EXPIRY)) {
        wtoken.setExpiryTimestamp(((Number) params.get(KEY_EXPIRY)).longValue() / Constants.TIME_UNITS_PER_MS);
      } else if (0 == DEFAULT_TTL) {
        throw new WarpScriptException(getName() + " missing '" + KEY_TTL + "' or '" + KEY_EXPIRY + "'.");
      } else {
        wtoken.setExpiryTimestamp(wtoken.getIssuanceTimestamp() + DEFAULT_TTL);
      }

      if (null != params.get(KEY_OWNER)) {
        wtoken.setOwnerId(encoder.toByteBuffer(params.get(KEY_OWNER).toString()));
      }
      
      if (null != params.get(KEY_PRODUCER)) {
        wtoken.setProducerId(encoder.toByteBuffer(params.get(KEY_PRODUCER).toString()));
      }
      
      if (null != params.get(KEY_ATTRIBUTES)) {
        if (!(params.get(KEY_ATTRIBUTES) instanceof Map)) {
          throw new WarpScriptException(getName() + " expects '" + KEY_ATTRIBUTES + "' to be a map.");
        }
        for (Entry<Object,Object> entry: ((Map<Object,Object>) params.get(KEY_ATTRIBUTES)).entrySet()) {
          if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects '" + KEY_ATTRIBUTES + "' to be a map of STRING keys and values.");
          }
          wtoken.putToAttributes(entry.getKey().toString(), entry.getValue().toString());
        }
      }

      if (null != params.get(KEY_LABELS)) {
        if (!(params.get(KEY_LABELS) instanceof Map)) {
          throw new WarpScriptException(getName() + " expects '" + KEY_LABELS + "' to be a map.");
        }
        for (Entry<Object,Object> entry: ((Map<Object,Object>) params.get(KEY_LABELS)).entrySet()) {
          if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
            throw new WarpScriptException(getName() + " expects '" + KEY_LABELS + "' to be a map of STRING keys and values.");
          }
          wtoken.putToLabels(entry.getKey().toString(), entry.getValue().toString());
        }
      }

      token = wtoken;
    } else {
      throw new WarpScriptException(getName() + " expects a key '" + KEY_TYPE + "' with value READ or WRITE in the parameter map.");
    }  

    return token;    
  }
}
