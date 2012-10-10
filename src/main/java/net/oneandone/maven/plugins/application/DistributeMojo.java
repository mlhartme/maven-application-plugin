/**
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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import net.oneandone.sushi.util.Separator;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.Node;
import org.apache.maven.plugin.MojoExecutionException;

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

        for (String machine : Separator.COMMA.split(distribute)) {
            try {
                uri = new URI(machine);
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
        dest.deleteFileOpt();
        src.move(dest);
    }

    private void copy(Node src, Node dest) throws IOException {
        getLog().info("  copy " + src.getURI() + " " + dest.getURI());
        src.copyFile(dest);
        dest.setPermissions(src.getPermissions());
    }
}
