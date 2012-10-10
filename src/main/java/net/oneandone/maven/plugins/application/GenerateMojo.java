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

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.jar.Attributes;

import javassist.NotFoundException;
import net.oneandone.sushi.util.Separator;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import net.oneandone.sushi.archive.Archive;
import net.oneandone.sushi.archive.ArchiveException;
import net.oneandone.sushi.fs.World;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import net.oneandone.sushi.xml.Builder;
import net.oneandone.sushi.xml.Dom;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/**
 * Generates an application file. Merges dependency jars into a single file, prepended with a launch shell script.
 *
 * @phase package
 * @goal generate
 * @description Generates an application file
 * @requiresDependencyResolution runtime
 */
public class GenerateMojo extends BaseMojo {
    /**
     * Main class to be launched. Specified as a fully qualified Java Class name. Similar to the main class
     * specified when you start the JVM.
     *
     * @parameter
     * @required
     */
    private String main;

    /**
     * Fixed options passed to Java VM. You can use this to make local shell variables used in the launch
     * script available Java. E.g. use "-Dapp=$APP" to make the fully qualified application name
     * available as a system property "app". Another example: use "-Dapporig=$0" to get the original name
     * this application was invoked with.
     *
     * @parameter expression=""
     */
    private String options;

    /**
     * Dependency jar entries to be concatenated before adding them to the application file. Comma-separated list of patterns.
     *
     * @parameter expression=""
     */
    private String concat = "";

    /**
     * Dependency jar entries that will not be added to the application file. Comma-separated list of patterns.
     *
     * @parameter expression=""
     */
    private String remove = "";

    /**
     * Dependency jar file entries that may overlap: the last entry will be added to the application file, all
     * previous entries get lost. Comma-separated list of patterns.
     *
     * @parameter expression=""
     */
    private String overwrite = "";

    /**
     * Dependency jar file entries that may be duplicates if they are equal. Comma-separated list of patterns.
     * Only one of the duplicates will be added to the application file.
     *
     * @parameter expression=""
     */
    private String equal = "";

    /**
     * Classifier to deploy application files with.
     * Specify a different value if you want to deploy multiple applications.
     *
     * @parameter default-value="application"
     *
     */
    private String classifier = "";

    /**
     * Name of the Java Executable to be invoked by the script. Only a file name, without path.
     *
     * @parameter default-value="java";
     */
    private String java = "";

    /**
     * Path to search the Java Executable. Defaults to the default search path. Change this variable to specify a different location
     * to search for java.
     *
     * @parameter default-value="$PATH";
     */
    private String path = "";

    /**
     * True to remove unused classes from that application file.
     *
     * @parameter default-value="false"
     */
    private boolean strip;

    /**
     * @parameter default-value=""
     */
    private String extraRoots = "";

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
     * @parameter default-value="${project.build.directory}/${project.build.finalName}.jar"
     */
    private String projectJar;

    /**
     * @parameter default-value="${project.build.directory}/application-strip.log"
     */
    private String stripLog;

    /**
     * Internal parameter.
     * @parameter property="project"
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
        this(new World(), null, null, null, null, null, null);
    }

    public GenerateMojo(World world, String name, FileNode dir, String main, String classifier, String java, String path) {
        super(world);
        this.name = name;
        this.dir = dir;
        this.main = main;
        this.classifier = classifier;
        this.java = java;
        this.path = path;
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
        validate(name);
        // do not wipe the directory because other plugins might already have copied stuff into it
        dir.mkdirsOpt();
        script();
        jar();
        getLog().info(">" + size(getFile().toPath().toFile()) + getFile());
        verify();
        projectHelper.attachArtifact(project, "sh", classifier, getFile().toPath().toFile());
    }

    private void verify() throws MojoExecutionException {
        URL url;
        URLClassLoader loader;
        Class<?> clazz;

        try {
            url = getFile().toPath().toFile().toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException();
        }
        loader = new URLClassLoader(new URL[] { url });
        try {
            clazz = loader.loadClass(main);
        } catch (ClassNotFoundException e) {
            throw new MojoExecutionException("main class not found: " + main, e);
        }
        try {
            clazz.getDeclaredMethod("main", String[].class);
        } catch (NoSuchMethodException e) {
            throw new MojoExecutionException("main class has no main(String[]) method: " + main, e);
        }
    }

    private void script() throws IOException {
        Node file;
        List<String> lines;

        lines = new ArrayList<>();
        lines.addAll(Arrays.asList(
                "#!/bin/sh",
                // resolve symlinks
                "APP=\"$0\"",
                "while [ -h \"$APP\" ] ; do",
                "  ls=$(ls -ld \"$APP\")",
                "  link=$(expr \"$ls\" : '.*-> \\(.*\\)$')",
                "  if expr \"$link\" : '/.*' > /dev/null; then",
                "    APP=\"$link\"",
                "  else",
                "    APP=$(dirname \"$APP\")\"/$link\"",
                "  fi",
                "done",
                // will be overridden with the configured name
                "NAME=$(basename \"$APP\")",
                "APP=$(dirname \"$APP\")",
                "APP=$(cd \"$APP\" && pwd)",
                "APP=\"$APP/$NAME\"",

                // make pom configuration available for "extensions:"
                "PATH=" + path,
                "JAVA=" + java,
                "OPTIONS=" + options,
                "MAIN=" + main,
                "NAME=" + name
                ));
        lines.addAll(extensions);

        // Notes
        // * $OPTIONS before getOptsVar to allow users to override built-in options
        // * reference jar via $APP to have symbolic links eleminated
        // * do not call with -jar to allow classpath modifications
        lines.add("$JAVA $OPTIONS $" + getOptsVar() + " -cp \"$APP\" $MAIN \"$@\"");
        // explicitly quit the script because I want to append to this file:
        lines.add("exit $?");
        file = getFile();
        file.writeLines(lines);
        file.setPermissions("rwxrw-rw-");
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
        final String suffix = "_OPTS";
        StringBuilder builder;
        char c;

        builder = new StringBuilder(name.length() + suffix.length());
        for (int i = 0; i < name.length(); i++) {
            c = name.charAt(i);
            if (Character.isLetter(c) || (i > 0 && Character.isDigit(c))) {
                builder.append(Character.toUpperCase(c));
            } else {
                builder.append('_');
            }
        }
        builder.append(suffix);
        return builder.toString();
    }

    public void jar() throws IOException, MojoExecutionException {
        Archive archive;
        List<String> roots;

        archive = Archive.createJar(world);
        addDependencies(archive);
        if (!archive.data.join(main.replace('.', '/') + ".class").isFile()) {
            throw new MojoExecutionException("main class not found: " + main);
        }
        mainAttributes(archive.manifest.getMainAttributes());
        if (strip) {
            roots = Separator.COMMA.split(extraRoots);
            roots.add(main + ".main");
            try {
                Stripper.run(archive, roots, world.file(stripLog)) /* TODO .warnings() */;
            } catch (NotFoundException e) {
                throw new MojoExecutionException("class not found", e);
            }
        }
        try (OutputStream dest = getFile().createAppendStream()) {
            archive.save(dest);
        }
    }

    private static String gav(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
    }

    private static String size(File file) {
        return Strings.padLeft("" + ((file.length() + 512) / 1024), 5) + " kb ";
    }

    private void addDependencies(Archive archive) throws IOException, MojoExecutionException {
        Document plexus;
        Sources sources;
        File file;
        Archive add;
        Node jar;
        List<String> duplicatePaths;

        plexus = null;
        sources = new Sources();
        duplicatePaths = new ArrayList<String>();
        for (Artifact artifact : getDependencies()) {
            getLog().info("+" + size(artifact.getFile()) + gav(artifact));
            file = artifact.getFile();
            if (file == null) {
                throw new IllegalStateException("unresolved dependency: " + gav(artifact) + ".jar");
            }
            jar = world.file(file);
            add = Archive.loadJar(jar);
            sources.addAll(add.data, artifact);
            removeFiles(add.data);
            concat(add.data, archive.data);
            copy(add.data, archive.data, duplicatePaths);
            archive.mergeManifest(add.manifest);
            plexus = plexusMerge(archive.data, plexus);
        }
        if (duplicatePaths.size() > 0) {
            sources.retain(duplicatePaths);
            throw new MojoExecutionException("duplicate files:\n" + sources.toString());
        }
        plexusSave(archive.data, plexus);
    }

    private static final String ROOT = "component-set";
    private static final String COMPONENTS = "components";

    private void copy(Node srcdir, Node destdir, List<String> duplicates) throws IOException, MojoExecutionException {
        List<Node> mayEqual;
        List<Node> mayOverwrite;
        Node destfile;
        String relative;

        mayEqual = find(srcdir, equal);
        mayOverwrite = find(srcdir, overwrite);
        for (Node srcfile : srcdir.find("**/*")) {
            relative = srcfile.getRelative(srcdir);
            destfile = destdir.join(relative);
            if (srcfile.isDirectory()) {
                destfile.mkdirsOpt();
            } else {
                if (destfile.exists()) {
                    if (srcfile.diff(destfile)) {
                        if (mayOverwrite.contains(srcfile)) {
                            getLog().debug("overwrite different " + relative);
                            destfile.deleteFile();
                            srcfile.copyFile(destfile);
                        } else {
                            duplicates.add(relative);
                        }
                    } else {
                        if (mayOverwrite.contains(srcfile)) {
                            getLog().debug("overwrite equal " + relative);
                        } else if (mayEqual.contains(srcfile)) {
                            getLog().debug("equal " + relative);
                        } else {
                            duplicates.add(relative);
                        }
                    }
                } else {
                    srcfile.copyFile(destfile);
                }
            }
        }
    }

    private static List<String> split(String str) throws MojoExecutionException {
        List<String> result;

        result = Separator.COMMA.split(str);
        for (String item : result) {
            if (item.contains(" ")) {
                throw new MojoExecutionException(
                        "Invalid space character in configuration value " + str + "\n" +
                        "Use commas to separate multiple entries.");
            }
        }
        return result;
    }

    private List<Node> find(Node srcdir, String property) throws IOException, MojoExecutionException {
        List<Node> mayOverwrite;

        mayOverwrite = new ArrayList<Node>();
        for (String path : split(property)) {
            mayOverwrite.addAll(srcdir.find(path));
        }
        return mayOverwrite;
    }

    private void removeFiles(Node srcdir) throws IOException, MojoExecutionException {
        for (String path : split(remove)) {
            removeFiles(srcdir, path);
        }
    }

    private void removeFiles(Node srcdir, String path) throws IOException {
        for (Node srcfile : srcdir.find(path)) {
            if (srcfile.isDirectory()) {
                // skip - I'd delete all contained files
            } else {
                getLog().debug("removing " + srcfile);
                srcfile.deleteFile();
            }
        }
    }

    private void concat(Node srcdir, Node destdir) throws IOException, MojoExecutionException {
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
        lf = srcdir.getWorld().os.lineSeparator.getSeparator();
        builder = new StringBuilder();
        builder.append(src.readString());
        idx = builder.lastIndexOf(lf);
        if (idx + lf.length() != builder.length()) {
            builder.append(lf);
        }
        builder.append(dest.readString());
        dest.writeString(builder.toString());
        src.deleteFile();
        getLog().debug("merged " + path + ":\n" + builder);
    }

    private Node plexusFile(Node root) {
        return root.join("META-INF/plexus/components.xml");
    }

    private void plexusSave(Node root, Document plexus) throws IOException {
        if (plexus != null) {
            getLog().debug("merged plexus components");
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
            plexus = world.getXml().getBuilder().createDocument(ROOT);
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
                throw new IOException("unknown element: " + e.getTagName());
            }
        }
        file.deleteFile();
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
            return "unknown organization";
        }
    }

    public static String getUserEmail() {
        String hostname;

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // this happens if your hostname is not in /etc/hosts and not resolvable via dns.
            hostname = "unknownhost";
        }
        return System.getProperty("user.name") + "@" + hostname;
    }
}
