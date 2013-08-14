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

import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Copies an application file to some setup directory.
 */
@Mojo(name = "update")
public class UpdateMojo extends BaseMojo {
    /**
     * Directory where to install the symlink.
     */
    @Parameter(required = true)
    private String bin;

    /**
     * Name of the symlink created in bin.
     */
    @Parameter
    private String symlink;

    /**
     * Release version to download from Maven repository. Local version is used when not specified.
     */
    @Parameter
    private String release;

    /**
     * Directory where to keep version of the file.
     */
    @Parameter(required = true)
    private String versions;

    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    public UpdateMojo() {
        this(new World());
    }

    public UpdateMojo(World world) {
        super(world);
    }

    public void execute() throws MojoExecutionException {
        try {
            doExecute();
        } catch (IOException e) {
            throw new MojoExecutionException("cannot deploy application: " + e.getMessage(), e);
        }
    }

    public void doExecute() throws IOException, MojoExecutionException {
        FileNode src;
        FileNode dest;
        FileNode link;

        if (release == null) {
            src = getFile();
        } else {
            src = null; // TODO
        }
        src.checkFile();
        dest = world.file(versions).join(project.getArtifactId() + "-"
                + Strings.removeRightOpt(project.getVersion(), "-SNAPSHOT") + "-" + classifier + "." + type);
        link = world.file(bin).join(symlink == null ? name : symlink);
        if (dest.exists()) {
            dest.deleteFile();
            getLog().info("U " + dest.getAbsolute());
        } else {
            getLog().info("A " + dest.getAbsolute());
        }
        src.copyFile(dest);
        if (link.exists()) {
            if (link.resolveLink().equals(dest)) {
                // the link is re-created to point to the same file, so from the user's perspective, it is not updated.
            } else {
                getLog().info("U " + link.getAbsolute());
            }
            link.deleteFile();
        } else {
            getLog().info("A " + link.getAbsolute());
        }
        dest.link(link);
    }
}
