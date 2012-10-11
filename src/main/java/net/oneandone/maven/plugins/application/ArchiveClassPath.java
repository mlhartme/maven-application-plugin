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
