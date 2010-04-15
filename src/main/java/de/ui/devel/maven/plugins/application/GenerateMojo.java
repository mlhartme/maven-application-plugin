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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import de.ui.sushi.archive.Archive;
import de.ui.sushi.archive.ArchiveException;
import de.ui.sushi.fs.IO;
import de.ui.sushi.fs.Node;
import de.ui.sushi.fs.file.FileNode;
import de.ui.sushi.util.Strings;
import de.ui.sushi.xml.Builder;
import de.ui.sushi.xml.Dom;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Generates an application file.
 * 
 * @phase package
 * @goal generate
 * @description Generates an application file
 * @requiresDependencyResolution runtime
 */
public class GenerateMojo extends Application {
    /**
     * Main class to be launched.
     * 
     * @parameter
     * @required
     */
    private String main;

    /**
     * Fixed options passed to Java VM. E.g. "-Dfoo=bar"
     * 
     * @parameter expression=""
     */
    private String options;

    /**
     * Jar file entries to be concatenated in merged jar. Comma-separated list of patterns.
     * 
     * @parameter expression=""
     */
    private String concat = "";
    
    /**
     * Jar file entries to removed from merged jar. Comma-separated list of patterns.
     * 
     * @parameter expression=""
     */
    private String remove = "";
    
    /** 
     * Jar file entries that may overlap; the last entry wins, it overwrites previous entries
     * 
     * @parameter expression="" 
     */
    private String overwrite = "";

    /**
     * Classifier to deploy applications with.
     * Specify a different value if you want to deploy multiple applications.
     *
     * @parameter default-value="application"
     *
     */
    private String classifier = "";

    /**
     * Copied verbatim to the launch code right before the final Java call,
     * placing each extension on a new line. 
     * You have access to the following script variables: 
     *   MAIN (the main class as specified in your pom), 
     *   NAME (application name as specified in your pom), 
     *   OPTIONS (as specified in your pom) and 
     *   APP (absolute normalized path to the application file).
     * 
     * Note that these variables are not exported, to access them from your application, you have to
     * turn them into properties!
     * 
     * @parameter
     */
    private List<String> extensions = new ArrayList<String>();
    
    /**
     * Scopes to include in compound jar. There's usually no need to touch this option.
     * Defaults to "compile" and "runtime".
     * 
     * @parameter
     */
    private List<String> scopes = Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);
    
    /**
     * @parameter expression="${project.build.directory}/${project.build.finalName}.jar"
     */
    private String projectJar;
    
    /**
     * Internal parameter.
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Maven ProjectHelper
     *
     * @component
     */
    protected MavenProjectHelper projectHelper;

    public GenerateMojo() {
        this(new IO(), null, null, null, null);
    }

    public GenerateMojo(IO io, String name, Node dir, String main, String classifier) {
        super(io);
        this.name = name;
        this.dir = dir;
        this.main = main;
        this.classifier = classifier;
        this.options = "";
    }

    public void execute() throws MojoExecutionException {
        try {
            doExecute();
        } catch (IOException e) {
            throw new MojoExecutionException("cannot generate application: " + e.getMessage(), e);
        }
    }
    
    public void doExecute() throws IOException, MojoExecutionException {
        getLog().info("generating application: " + getFile());
        validate(name);
        // do not wipe the directory because other plugins might already have copied stuff into it
        dir.mkdirsOpt();
        script();
        jar();
        projectHelper.attachArtifact(project, "sh", classifier, ((FileNode) getFile()).getFile());
    }

    private void script() throws IOException {
        Node file;
        List<String> lines;
        
        lines = new ArrayList<String>();
        lines.addAll(Arrays.asList(
                "#!/bin/sh",
                // resolve symlinks
                "APP=\"$0\"",
                "while [ -h \"$APP\" ] ; do",
                "  ls=`ls -ld \"$APP\"`",
                "  link=`expr \"$ls\" : '.*-> \\(.*\\)$'`",
                "  if expr \"$link\" : '/.*' > /dev/null; then",
                "    APP=\"$link\"",
                "  else",
                "    APP=`dirname \"$APP\"`\"/$link\"",
                "  fi",
                "done",
                // will be overridden with the configured name
                "NAME=$(basename \"$APP\")",
                "APP=$(dirname \"$APP\")",
                "APP=$(cd \"$APP\" && pwd)",
                "APP=\"$APP/$NAME\"",

                // make pom configuration available for "extensions:" 
                "OPTIONS=" + options,
                "MAIN=" + main,
                "NAME=" + name
                ));
        lines.addAll(extensions);
        // reference jar via $APP to have symbolic links eleminated;
        // do not call with -jar to allow classpath modifications
        lines.add("java $" + getOptsVar() + " $OPTIONS -cp \"$APP\" $MAIN \"$@\"");
        // explicitly quit the script because I want to append to this file:
        lines.add("exit $?");
        file = getFile();
        file.writeLines(lines);
        file.setMode(0755);
    }
    
    public static void validate(String name) throws MojoExecutionException {
        for (int i = 0, max = name.length(); i < max; i++) {
            if (Character.isWhitespace(name.charAt(i))) {
                throw new MojoExecutionException("invalid whitespace in application name: '" + name + "'");
            }
        }
    }
    
    //--

    private List<Artifact> getDependencies() {
        List<Artifact> artifacts;
        Artifact a;
        
        artifacts = new ArrayList<Artifact>();
        for (Iterator<?> i = project.getArtifacts().iterator(); i.hasNext(); ) {
            a = (Artifact) i.next();
            if (scopes.contains(a.getScope())) {
                artifacts.add(a);
            }
        }
        a = project.getArtifact();
        if (a.getFile() == null) {
            // happens if application:generate is called directly from the command line
            a.setFile(new File(projectJar));
        }            
        artifacts.add(a);
        return artifacts;
    }

    public String getOptsVar() {
        return name.toUpperCase().replace('-', '_') + "_OPTS";
    }
    
    public void jar() throws IOException, MojoExecutionException {
        Archive archive;
        List<Duplicate> duplicates;
        OutputStream dest;
        
        archive = Archive.createJar(io);
        duplicates = new ArrayList<Duplicate>();
        archive = loadDependencies(archive, duplicates);
        if (duplicates.size() > 0) {
            throw new MojoExecutionException("duplicate file(s): " + duplicates);
        }
        mainAttributes(archive.manifest.getMainAttributes());
        dest = getFile().createAppendStream();
        archive.save(dest);
        dest.close();
    }

    private Archive loadDependencies(Archive archive, List<Duplicate> duplicates) throws IOException {
        Document plexus;
        File file;
        Archive add;
        
        plexus = null;
        for (Artifact artifact : getDependencies()) {
            getLog().info("adding " + artifact);
            file = artifact.getFile();
            if (file == null) {
                throw new RuntimeException("unresolved dependency: " + 
                        artifact.getGroupId() + " " + artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar");
            }
            add = Archive.loadJar(io.file(file));
            removeFiles(add.data);
            concat(add.data, archive.data);
            copy(add.data, archive.data, duplicates);
            archive.mergeManifest(add.manifest);
            plexus = plexusMerge(archive.data, plexus);
        }
        plexusSave(archive.data, plexus);
        return archive;
    }
    
    private static final String ROOT = "component-set";
    private static final String COMPONENTS = "components";

    private void copy(Node srcdir, Node destdir, List<Duplicate> duplicates) throws IOException {
        List<Node> mayOverwrite;
        Node destfile;
        String relative;
            
        mayOverwrite = mayOverwrite(srcdir);
        for (Node srcfile : srcdir.find("**/*")) {
            relative = srcfile.getRelative(srcdir);
            destfile = destdir.join(relative);
            if (srcfile.isDirectory()) {
                destfile.mkdirsOpt();
            } else {
                if (destfile.exists()) {
                    if (srcfile.diff(destfile)) {
                        if (mayOverwrite.contains(srcfile)) {
                            getLog().debug("overwriting " + relative);
                            destfile.delete();
                            srcfile.copyFile(destfile);
                        } else {
                            Duplicate.add(duplicates, relative, srcdir);
                        }
                    } else {
                        getLog().debug("same file: " + relative);
                    }
                } else {
                    srcfile.copyFile(destfile);
                }
            }
        }
    }

    private static List<String> split(String str) {
        return Strings.trim(Strings.split(",", str));
    }
    
    private List<Node> mayOverwrite(Node srcdir) throws IOException {
        List<Node> mayOverwrite;

        mayOverwrite = new ArrayList<Node>();
        for (String path : split(overwrite)) {
            mayOverwrite.addAll(srcdir.find(path));
        }
        return mayOverwrite;
    }

    private void removeFiles(Node srcdir) throws IOException {
        for (String path : split(remove)) {
            removeFiles(srcdir, path);
        }
    }

    private void removeFiles(Node srcdir, String path) throws IOException {
        for (Node srcfile : srcdir.find(path)) {
            getLog().debug("removing " + srcfile);
            srcfile.delete();
        }
    }

    private void concat(Node srcdir, Node destdir) throws IOException {
        for (String path : split(concat)) {
            getLog().debug("concatenating " + path);
            concat(srcdir, destdir, path);
        }
    }

    private void concat(Node srcdir, Node destdir, String path) throws IOException {
        for (Node srcfile : srcdir.find(path)) {
            concatOne(srcdir, destdir, srcfile.getRelative(srcdir));
        }
    }

    private void concatOne(Node srcdir, Node destdir, String path) throws IOException {
        Node src;
        Node dest;
        String lf;
        StringBuilder builder;
        int idx;
        
        src = srcdir.join(path);
        dest = destdir.join(path);
        if (!dest.exists()) {
            dest.getParent().mkdirsOpt();
            dest.writeString("");
        }
        lf = srcdir.getIO().os.lineSeparator;
        builder = new StringBuilder();
        builder.append(src.readString());
        idx = builder.lastIndexOf(lf);
        if (idx + lf.length() != builder.length()) {
            builder.append(lf);
        }
        builder.append(dest.readString());
        dest.writeString(builder.toString());
        src.delete();
        getLog().debug("merged " + path + ":\n" + builder);
    }
    
    private Node plexusFile(Node root) {
        return root.join("META-INF/plexus/components.xml");
    }
    
    private void plexusSave(Node root, Document plexus) throws IOException {
        if (plexus != null) {
            getLog().info("merged plexus components");
            plexusFile(root).writeXml(plexus);
        }
    }

    private Document plexusMerge(Node root, Document plexus) throws IOException {
        Element componentSet;
        Element components;
        Node file;
        
        file = plexusFile(root);
        if (!file.exists()) {
            return plexus;
        }
        if (plexus == null) {
            plexus = io.getXml().builder.createDocument(ROOT);
            components = Builder.element(plexus.getDocumentElement(), COMPONENTS);
        } else {
            components = (Element) plexus.getDocumentElement().getFirstChild();
        }
        try {
            componentSet = file.readXml().getDocumentElement();
        } catch (SAXException e) {
            throw new IOException(file + ": " + e.getMessage());
        }
        if (!componentSet.getTagName().equals(ROOT)) {
            throw new IOException(file + ": expected " + ROOT);
        }
        for (Element e : Dom.getAllChildElements(componentSet)) {
            if (e.getTagName().equals(COMPONENTS)) {
                Builder.add(components, e.getChildNodes());
            } else {
                throw new IOException("unkown element: " + e.getTagName());
            }
        }
        file.delete();
        return plexus;
    }
    
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");

    private void mainAttributes(Attributes attributes) throws ArchiveException {
        attributes.putValue("Specification-Title", project.getName());
        attributes.putValue("Specification-Version", project.getVersion());
        attributes.putValue("Specification-Vendor", getOrganization());
        attributes.putValue("Implementation-Title", project.getGroupId() + ":" + project.getArtifactId());
        attributes.putValue("Implementation-Version", DATE_FORMAT.format(new Date()));
        attributes.putValue("Implementation-Vendor", getUserEmail());
        attributes.putValue("Main-class", main);
    }

    private String getOrganization() {
        if (project.getOrganization() != null) { 
            return project.getOrganization().getName();
        } else {
            return "unkown organization";
        }
    }

    public static String getUserEmail() {
        // TODO: email address ...
        try {
            return System.getProperty("user.name") + "@" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
