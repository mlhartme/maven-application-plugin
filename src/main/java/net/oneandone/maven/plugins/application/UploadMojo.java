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
/**
 * This file is part of ${project.name}.
 *
 * ${project.name} is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ${project.name} is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ${project.name}.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.oneandone.maven.plugins.application;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

import com.jcraft.jsch.JSchException;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.NodeInstantiationException;
import net.oneandone.sushi.fs.OnShutdown;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.fs.ssh.SshNode;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Copies an application to some target directory and creates/updates a symlink pointing to it.
 * The symlink is mandatory, because it allows version updates without breaking running applications.
 * So it's not possible to only install the application file with this plugin.
 */
@Mojo(name = "upload")
public class UploadMojo extends BaseMojo {
    /**
     * Symlink pointing to the application. A local file or an ssh location
     */
    @Parameter(required = true)
    private String symlink;

    /**
     * Skip symlink update. Useful if you just need a new target file uploaded, but don't want everybody to use it.
     */
    @Parameter(defaultValue = "false", property = "skipSymlink")
    private boolean skipSymlink;

    /**
     * Directory where to store application. A local directory or an ssh location.
     * Optional, when not specified, the directory containing the symlink will be used.
     * The name of the application in the target file is <code>
     *   artifactId + "-" + Strings.removeRightOpt(version, "-SNAPSHOT") + "-" + classifier + "." + type);artifactId
     * </code>. Note that snapshot suffixes are stripped, i.e. updating an snapshot overwrites
     * the previous snapshots.
     */
    @Parameter
    private String target;

    /**
     * Version of the application artifact to resolve from Maven repositories.
     * When not specified, the artifact is picked from the build directory. This is useful to
     * test local builds.
     */
    @Parameter(property = "resolve")
    private String resolve;

    /**
     * Set permissions of uploaded file?
     */
    @Parameter(defaultValue = "false")
    private boolean uploadPermissions;

    @Parameter(property = "project", required = true, readonly = true)
    private MavenProject project;

    @Parameter(property = "localRepository", readonly = true)
    private ArtifactRepository localRepository;

    @Parameter(property = "project.remoteArtifactRepositories", readonly = true)
    private List<ArtifactRepository> remoteRepositories;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver resolver;

    public UploadMojo() {
        this(new World());
    }

    public UploadMojo(World world) {
        super(world);
    }

    public void execute() throws MojoExecutionException {
        try {
            doExecute();
        } catch (MojoExecutionException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("cannot deploy application: " + e.getMessage(), e);
        } finally {
            // Shutdown here, because otherwise Plexus might remove classes, that are needed for the shutdown hook
            // TODO: what if the plugin is executed twice?
            OnShutdown shutdown;

            shutdown = OnShutdown.get();
            Runtime.getRuntime().removeShutdownHook(shutdown);
            shutdown.run();
        }
    }

    public void doExecute()
            throws IOException, MojoExecutionException, ArtifactNotFoundException, ArtifactResolutionException, URISyntaxException, JSchException {
        String version;
        FileNode src;
        Node dest;
        Node link;
        String relative;

        if (resolve == null) {
            version = project.getVersion();
            src = getFile();
        } else {
            version = resolve;
            src = resolve(artifactFactory.createArtifactWithClassifier(
                    project.getGroupId(), project.getArtifactId(), version, type, classifier));
        }
        src.checkFile();
        link = node(symlink);
        dest = (target != null ? node(target) : link.getParent()).join(project.getArtifactId() + "-"
                + Strings.removeRightOpt(version, "-SNAPSHOT") + "-" + classifier + "." + type);
        if (dest.exists()) {
            dest.deleteFile();
            getLog().info("U " + dest.getURI());
        } else {
            getLog().info("A " + dest.getURI());
        }
        src.copyFile(dest);
        if (uploadPermissions) {
            dest.setPermissions(permissions);
        }
        if (skipSymlink) {
            getLog().info("symlink skipped");
        } else {
            if (link.exists()) {
                if (link.resolveLink().equals(dest)) {
                    // the link is re-created to point to the same file, so from the user's perspective, it is not updated.
                } else {
                    getLog().info("U " + link.getURI());
                }
                link.deleteFile();
            } else {
                getLog().info("A " + link.getURI());
            }
            relative = dest.getRelative(link.getParent());
            // TODO: sushi
            if (link instanceof SshNode) {
                ((SshNode) link).getRoot().exec(false, "cd", "/" + link.getParent().getPath(), "&&", "ln", "-s", relative, link.getName());
            } else {
                link.mklink(relative);
            }
        }
    }

    private Node node(String str) throws URISyntaxException, NodeInstantiationException {
        if (str.indexOf(':') > 0) {
            return world.node(str);
        } else {
            return world.file(str);
        }
    }

    private FileNode resolve(Artifact artifact) throws ArtifactNotFoundException, ArtifactResolutionException {
        resolver.resolve(artifact, remoteRepositories, localRepository);
        return world.file(artifact.getFile());
    }
}
