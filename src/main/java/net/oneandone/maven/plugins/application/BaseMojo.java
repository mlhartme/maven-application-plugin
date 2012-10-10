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

import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugin.AbstractMojo;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.Node;

/**
 */
public abstract class BaseMojo extends AbstractMojo {
    protected final World world;

    /**
     * Directory where to place the Launch Script and the executable Jar file.
     * Usually, there's no need to change the default value, which is target.
     *
     * @parameter expression="${project.build.directory}"
     */
    protected FileNode dir;

    /**
     * Name for the generated application file.
     *
     * @parameter expression="${project.artifactId}"
     */
    protected String name;

    public BaseMojo() {
        this(new World());
    }

    public BaseMojo(World world) {
        this.world = world;
    }

    public void setDir(String dir) {
        this.dir = world.file(dir);
    }

    public Node getDir() {
        return dir;
    }

    public FileNode getFile() {
        return dir.join(name);
    }
}
