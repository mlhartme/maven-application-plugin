/**
 * This file is part of maven-application-plugin.
 *
 * maven-application-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * maven-application-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with maven-application-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.oneandone.maven.plugins.application;

import net.oneandone.sushi.fs.file.FileNode;
import org.apache.maven.plugin.AbstractMojo;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.Node;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class BaseMojo extends AbstractMojo {
    protected final World world;

    /**
     * Directory where to place the Launch Script and the executable Jar file.
     * Usually, there's no need to change the default value, which is target.
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected FileNode dir;

    /**
     * Name for the generated application file.
     */
    @Parameter(defaultValue = "${project.artifactId}")
    protected String name;

    /**
     * Classifier to deploy application files with.
     * Specify a different value if you want to deploy multiple applications.
     */
    @Parameter(defaultValue = "application")
    protected String classifier = "";

    /**
     * Type to deploy application files with.
     */
    @Parameter(defaultValue = "sh")
    protected String type = "";

    /**
     * Permissions for application file.
     */
    @Parameter(defaultValue = "rwxr-xr-x")
    protected String permissions = "";

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
