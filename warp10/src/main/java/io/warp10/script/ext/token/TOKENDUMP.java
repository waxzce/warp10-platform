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

import org.apache.thrift.TBase;

import com.google.common.base.Charsets;

import io.warp10.continuum.Tokens;
import io.warp10.crypto.KeyStore;
import io.warp10.crypto.OrderPreservingBase64;
import io.warp10.crypto.SipHashInline;
import io.warp10.quasar.encoder.QuasarTokenDecoder;
import io.warp10.quasar.encoder.QuasarTokenEncoder;
import io.warp10.quasar.filter.exception.QuasarTokenException;
import io.warp10.quasar.token.thrift.data.ReadToken;
import io.warp10.quasar.token.thrift.data.TokenType;
import io.warp10.quasar.token.thrift.data.WriteToken;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

/**
 * Reads a token and generates a structure for TOKENGEN to recreate an identical token
 */
public class TOKENDUMP extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  private final QuasarTokenEncoder encoder = new QuasarTokenEncoder();
  private final QuasarTokenDecoder decoder;

  public static final String KEY_PARAMS = "params";
  
  private byte[] tokenAESKey = null;
  private byte[] tokenSipHashKey = null;
  
  public TOKENDUMP(String name) {
    super(name);
    decoder = null;
  }
  
  public TOKENDUMP(String name, KeyStore keystore) {
    super(name);
    tokenAESKey = keystore.getKey(KeyStore.AES_TOKEN);
    tokenSipHashKey = keystore.getKey(KeyStore.SIPHASH_TOKEN);
    long[] lkey = SipHashInline.getKey(tokenSipHashKey);
    decoder = new QuasarTokenDecoder(lkey[0], lkey[1], tokenAESKey);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    
    if (null == tokenAESKey || null == tokenSipHashKey) {
      throw new WarpScriptException(getName() + " cannot be used in this context.");
    }
    
    Object top = stack.pop();
    
    if (!(top instanceof String)) {
      throw new WarpScriptException(getName() + " expects a token on top of the stack.");
    }

    String tokenstr = top.toString();
    
    ReadToken rtoken = null;
    WriteToken wtoken = null;
    
    byte[] token = OrderPreservingBase64.decode(tokenstr.getBytes(Charsets.UTF_8));
    
    try {
      rtoken = decoder.decodeReadToken(token);
    } catch (QuasarTokenException qte) {
      try {
        wtoken = decoder.decodeWriteToken(token);
      } catch (Exception e) {
        throw new WarpScriptException(getName() + " invalid token.", e);
      }
    }
    
    String ident = encoder.getTokenIdent(tokenstr, this.tokenSipHashKey);

    Map<Object,Object> result = new HashMap<Object,Object>();
    result.put(TOKENGEN.KEY_TOKEN, tokenstr);
    result.put(TOKENGEN.KEY_IDENT, ident);
    result.put(KEY_PARAMS, mapFromToken(null != rtoken ? rtoken : wtoken));
    
    stack.push(result);
    
    return stack;
  }

  public Map<String,Object> mapFromToken(TBase token) {
    Map<String,Object> params = new HashMap<String,Object>();
    
    if (token instanceof ReadToken) {
      ReadToken rtoken = (ReadToken) token;
      params.put(TOKENGEN.KEY_TYPE, TokenType.READ.toString());
      params.put(TOKENGEN.KEY_OWNER, Tokens.getUUID(rtoken.getBilledId()));
      params.put(TOKENGEN.KEY_APPLICATION, rtoken.getAppName());
      params.put(TOKENGEN.KEY_ISSUANCE, rtoken.getIssuanceTimestamp());
      params.put(TOKENGEN.KEY_EXPIRY, rtoken.getExpiryTimestamp());
      

      if (rtoken.getOwnersSize() > 0) {
        List<String> owners = new ArrayList<String>();
        params.put(TOKENGEN.KEY_OWNERS, owners);
        for (ByteBuffer bb: rtoken.getOwners()) {
          owners.add(Tokens.getUUID(bb));
        }
      }
      
      if (rtoken.getProducersSize() > 0) {
        List<String> producers = new ArrayList<String>();
        params.put(TOKENGEN.KEY_PRODUCERS, producers);
        for (ByteBuffer bb: rtoken.getProducers()) {
          producers.add(Tokens.getUUID(bb));
        }
      }
      
      if (rtoken.getAppsSize() > 0) {
        List<String> applications = new ArrayList<String>();
        params.put(TOKENGEN.KEY_APPLICATIONS, applications);
        for (String app: rtoken.getApps()) {
          applications.add(app);
        }
      }
      
      if (rtoken.getAttributesSize() > 0) {
        Map<String,String> attr = new HashMap<String,String>(rtoken.getAttributes());
        params.put(TOKENGEN.KEY_ATTRIBUTES, attr);
      }

      if (rtoken.getLabelsSize() > 0) {
        Map<String,String> labels = new HashMap<String,String>(rtoken.getLabels());
        params.put(TOKENGEN.KEY_LABELS, labels);
      }
    } else {
      WriteToken wtoken = (WriteToken) token;
      
      params.put(TOKENGEN.KEY_TYPE, TokenType.WRITE.toString());
      params.put(TOKENGEN.KEY_OWNER, Tokens.getUUID(wtoken.getOwnerId()));
      params.put(TOKENGEN.KEY_PRODUCER, Tokens.getUUID(wtoken.getProducerId()));
      params.put(TOKENGEN.KEY_APPLICATION, wtoken.getAppName());
      params.put(TOKENGEN.KEY_ISSUANCE, wtoken.getIssuanceTimestamp());
      params.put(TOKENGEN.KEY_EXPIRY, wtoken.getExpiryTimestamp());

      if (wtoken.getAttributesSize() > 0) {
        Map<String,String> attr = new HashMap<String,String>(wtoken.getAttributes());
        params.put(TOKENGEN.KEY_ATTRIBUTES, attr);
      }

      if (wtoken.getLabelsSize() > 0) {
        Map<String,String> labels = new HashMap<String,String>(wtoken.getLabels());
        params.put(TOKENGEN.KEY_LABELS, labels);
      }
    }
    return params;
  }
}
