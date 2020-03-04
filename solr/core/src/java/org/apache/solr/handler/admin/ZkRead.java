/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.handler.admin;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.api.Command;
import org.apache.solr.api.EndPoint;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.BinaryResponseParser;
import org.apache.solr.common.MapWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.common.util.Utils;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.zookeeper.data.Stat;

import static org.apache.solr.common.params.CommonParams.OMIT_HEADER;
import static org.apache.solr.common.params.CommonParams.WT;
import static org.apache.solr.response.RawResponseWriter.CONTENT;
import static org.apache.solr.security.PermissionNameProvider.Name.COLL_READ_PERM;

/**Exposes the content of the Zookeeper
 *
 */
@EndPoint(path = "/cluster/zk/*",
    method = SolrRequest.METHOD.GET,
    permission = COLL_READ_PERM)
public class ZkRead {
  private final CoreContainer coreContainer;

  public ZkRead(CoreContainer coreContainer) {
    this.coreContainer = coreContainer;
  }

  @Command
  public void get(SolrQueryRequest req, SolrQueryResponse rsp) {
    String path = req.getPathTemplateValues().get("*");
    if (path == null || path.isEmpty()) path = "/";
    byte[] d = null;
    try {
      List<String> l = coreContainer.getZkController().getZkClient().getChildren(path, null, false);
      if (l != null && !l.isEmpty()) {
        String prefix = path.endsWith("/") ? path : path + "/";

        rsp.add(path, (MapWriter) ew -> {
          for (String s : l) {
            try {
              Stat stat = coreContainer.getZkController().getZkClient().exists(prefix + s, null, false);
              ew.put(s, (MapWriter) ew1 -> {
                ew1.put("version", stat.getVersion());
                ew1.put("aversion", stat.getAversion());
                ew1.put("children", stat.getNumChildren());
                ew1.put("ctime", stat.getCtime());
                ew1.put("cversion", stat.getCversion());
                ew1.put("czxid", stat.getCzxid());
                ew1.put("ephemeralOwner", stat.getEphemeralOwner());
                ew1.put("mtime", stat.getMtime());
                ew1.put("mzxid", stat.getMzxid());
                ew1.put("pzxid", stat.getPzxid());
                ew1.put("dataLength", stat.getDataLength());
              });
            } catch (Exception e) {
              ew.put("s", Collections.singletonMap("error", e.getMessage()));
            }
          }
        });

      } else {
        d = coreContainer.getZkController().getZkClient().getData(path, null, null, false);
        if (d == null || d.length == 0) {
          rsp.add(path, null);
          return;
        }

        Map<String, String> map = new HashMap<>(1);
        map.put(WT, "raw");
        map.put(OMIT_HEADER, "true");
        req.setParams(SolrParams.wrapDefaults(new MapSolrParams(map), req.getParams()));


        rsp.add(CONTENT, new ContentStreamBase.ByteArrayStream(d, null,
            d[0] == '{' ? CommonParams.JSON_MIME : BinaryResponseParser.BINARY_CONTENT_TYPE));

      }

    } catch (Exception e) {
      rsp.add(CONTENT, new ContentStreamBase.StringStream(Utils.toJSONString(Collections.singletonMap("error", e.getMessage()))));
    }
  }

  public static void main(String[] args) {
  }

}