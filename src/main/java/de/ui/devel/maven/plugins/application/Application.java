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

package de.ui.devel.maven.plugins.application;

import org.apache.maven.plugin.AbstractMojo;
import de.ui.sushi.fs.IO;
import de.ui.sushi.fs.Node;

/**
 */
public abstract class Application extends AbstractMojo {
    protected final IO io;
    
    /**
     * Directory where to place the Launch Script and the executable Jar file. 
     * Usually, there's no need to change the default value, which is target.
     *
     * @parameter expression="${project.build.directory}"
     */
    protected Node dir;
    
    /**
     * Name for the generated application, without extension.
     * 
     * @parameter expression="${project.artifactId}"
     */
    protected String name;

    public Application() {
        this(new IO());
    }

    public Application(IO io) {
        this.io = io;
    }

    public void setDir(String dir) {
        this.dir = io.file(dir);
    }

    public Node getDir() {
        return dir;
    }
    
    public Node getFile() {
        return dir.join(name);
    }
}
