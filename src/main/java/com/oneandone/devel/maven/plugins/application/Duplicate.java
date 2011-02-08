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

import java.util.ArrayList;
import java.util.List;

import com.oneandone.sushi.fs.Node;

public class Duplicate {
    private final String path;
    private final List<Node> sources;
    
    public Duplicate(String path, Node source) {
        this(path);
        sources.add(source);
    }
    
    public Duplicate(String path) {
        this.path = path;
        this.sources = new ArrayList<Node>();
    }
    
    @Override
    public String toString() {
        // TODO: source is not very useful because memory a memory node has a poor toString()
        return path;
    }
    
    public static void add(List<Duplicate> duplicates, String path, Node file) {
        for (Duplicate duplicate : duplicates) {
            if (path.equals(duplicate.path)) {
                duplicate.sources.add(file);
                return;
            }
        }
        duplicates.add(new Duplicate(path, file));
    }
}
