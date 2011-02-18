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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import net.sf.beezle.sushi.fs.World;
import net.sf.beezle.sushi.fs.Node;
import net.sf.beezle.sushi.util.Strings;

/**
 * Distributes application files.
 * 
 * @goal distribute
 */
public class DistributeMojo extends BaseMojo {
    /**
     * Comma separated list of targets. E.g. ssh://root@pumama.schlund.de/home/clustercontrol/launcher
     * 
     * @parameter
     * @required
     */
    private String distribute;
    
    /**
     * Base name for backup file. If not specified, no backup will be created
     *  
     * @parameter
     */
    private String backup;
    
    /**
     * Pattern to search root directory of the respective distribute node for lock files.
     * Distribution is aborted if the pattern matches one or more files.

     * @parameter
     */
    private String lockfile;
    
    public DistributeMojo() {
        this(new World());
    }

    public DistributeMojo(World world) {
        super(world);
    }

    public void execute() throws MojoExecutionException {
        try {
            doExecute();
        } catch (IOException e) {
            throw new MojoExecutionException("cannot distribute application: " + e.getMessage(), e);
        }
    }
    
    public void doExecute() throws IOException, MojoExecutionException {
        URI uri;

        for (String machine : Strings.split(",", distribute)) {
            try {
                uri = new URI(machine.trim());
            } catch (URISyntaxException e) {
                throw new MojoExecutionException("invalid distribution target: " + machine.trim(), e);
            }
            deploy(world.node(uri));
        }
    }
    
    private void deploy(Node dest) throws IOException {
        List<Node> lst;

        getLog().info("distributing to " + dest.getURI());
        if (lockfile != null) {
            lst = dest.getRootNode().find(lockfile);
            if (lst.size() > 0) {
                throw new IOException("locked: " + lst);
            }
        }
        if (backup != null && dest.join(name).exists()) {
            move(dest.join(name), dest.join(backup));
        }
        copy(getFile(), dest.join(name));
    }

    private void move(Node src, Node dest) throws IOException {
        getLog().info("  move " + src.getURI() + " " + dest.getURI());
        dest.deleteOpt();
        src.move(dest);
    }
    
    private void copy(Node src, Node dest) throws IOException {
        getLog().info("  copy " + src.getURI() + " " + dest.getURI());
        src.copyFile(dest);
        dest.setMode(src.getMode());
    }
}
