package net.oneandone.maven.plugins.application;

import javassist.ClassPath;
import javassist.NotFoundException;
import net.oneandone.sushi.archive.Archive;
import net.oneandone.sushi.fs.CreateInputStreamException;
import net.oneandone.sushi.fs.FileNotFoundException;
import net.oneandone.sushi.fs.Node;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

class ArchiveClassPath implements ClassPath {
    private final Archive archive;

    public ArchiveClassPath(Archive archive) {
        this.archive = archive;
    }

    @Override
    public InputStream openClassfile(String classname) throws NotFoundException {
        try {
            return node(classname).createInputStream();
        } catch (FileNotFoundException e) {
            return null;
        } catch (CreateInputStreamException e) {
            throw new NotFoundException(e.getMessage(), e);
        }
    }

    @Override
    public URL find(String classname) {
        Node node;

        node = node(classname);
        try {
            if (!node.exists()) {
                return null;
            }
            // TODO: node.getUri().getUrl() fails with unknown protocol "mem:" ... however, the result is only tested for != null
            return new URL("file://" + node.getPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
    }

    private Node node(String classname) {
        return archive.data.join(classname.replace('.', '/') + ".class");
    }
}
