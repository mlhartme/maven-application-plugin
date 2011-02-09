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
import java.util.List;
import java.util.Map;

import com.oneandone.sushi.fs.Node;

public class Sources {
    private Map<String, List<Node>> map;

    public Sources() {
        this.map = new HashMap<String, List<Node>>();
    }

    public void add(String path, Node source) {
        List<Node> lst;

        lst = map.get(path);
        if (lst == null) {
            lst = new ArrayList<Node>();
            map.put(path, lst);
        }
        lst.add(source);
    }

    public void addAll(Node root, Node source) throws IOException {
        for (Node node : root.find("**/*")) {
            add(node.getRelative(root), source);
        }
    }

    public List<Node> get(String path) {
        return map.get(path);
    }
}
