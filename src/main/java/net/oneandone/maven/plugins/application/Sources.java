/*
 * Copyright 1&1 Internet AG, https://github.com/1and1/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.oneandone.maven.plugins.application;

import net.oneandone.sushi.fs.Node;
import org.apache.maven.artifact.Artifact;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Sources {
    private Map<String, List<Artifact>> map;

    public Sources() {
        this.map = new HashMap<String, List<Artifact>>();
    }

    public void add(String path, Artifact source) {
        List<Artifact> lst;

        lst = map.get(path);
        if (lst == null) {
            lst = new ArrayList<Artifact>();
            map.put(path, lst);
        }
        lst.add(source);
    }

    public void addAll(Node<?> root, Artifact source) throws IOException {
        for (Node node : root.find("**/*")) {
            add(node.getRelative(root), source);
        }
    }

    public List<Artifact> get(String path) {
        return map.get(path);
    }

    public void retain(List<String> paths) {
        Iterator<Map.Entry<String, List<Artifact>>> iter;
        Map.Entry<String, List<Artifact>> entry;

        iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            entry = iter.next();
            if (!paths.contains(entry.getKey())) {
                iter.remove();
            }
        }
    }

    public String toString() {
        StringBuilder builder;
        Iterator<Map.Entry<String, List<Artifact>>> iter;
        Iterator<Map.Entry<String, List<Artifact>>> sub;
        Map.Entry<String, List<Artifact>> entry;
        List<Artifact> sources;

        builder = new StringBuilder();
        while (true) {
            iter = map.entrySet().iterator();
            if (!iter.hasNext()) {
                break;
            }
            entry = iter.next();
            sources = entry.getValue();
            builder.append("  ").append(sources.toString()).append(":\n");
            builder.append("    ").append(entry.getKey()).append('\n');
            iter.remove();
            sub = map.entrySet().iterator();
            while (sub.hasNext()) {
                entry = sub.next();
                if (sources.equals(entry.getValue())) {
                    builder.append("    ").append(entry.getKey()).append('\n');
                    sub.remove();
                }
            }
        }
        return builder.toString();
    }
}
