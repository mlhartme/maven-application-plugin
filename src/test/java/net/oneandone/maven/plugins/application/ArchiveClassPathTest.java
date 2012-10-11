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
