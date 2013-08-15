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
        dest.setPermissions(permissions);
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

    private Node node(String str) throws URISyntaxException, NodeInstantiationException {
        if (str.startsWith("ssh:")) {
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
