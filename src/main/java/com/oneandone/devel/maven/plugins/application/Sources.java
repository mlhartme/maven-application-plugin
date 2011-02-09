/*
 * Copyright 1&1 Internet AG, http://www.1and1.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oneandone.devel.maven.plugins.application;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.oneandone.sushi.fs.Node;
import org.apache.maven.artifact.Artifact;

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

    public void addAll(Node root, Artifact source) throws IOException {
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
