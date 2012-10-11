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

import javassist.ClassPool;
import net.oneandone.sushi.archive.Archive;
import net.oneandone.sushi.fs.World;
import org.junit.Test;

import static org.junit.Assert.assertNull;

public class ArchiveClassPathTest {
    @Test
    public void classPool() {
        World world;
        Archive archive;
        ClassPool pool;

        world = new World();
        archive = Archive.createJar(world);
        pool = new ClassPool();
        pool.appendClassPath(new ArchiveClassPath(archive));
        assertNull(pool.getOrNull("nosuchclass"));
    }

}
