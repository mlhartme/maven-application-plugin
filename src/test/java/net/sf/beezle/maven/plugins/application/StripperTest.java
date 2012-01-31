package net.sf.beezle.maven.plugins.application;

import net.sf.beezle.maven.plugins.application.data.Empty;
import net.sf.beezle.mork.classfile.ClassRef;
import net.sf.beezle.mork.classfile.MethodRef;
import net.sf.beezle.mork.classfile.Repository;
import net.sf.beezle.sushi.fs.World;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StripperTest {
    @Test
    public void empty() throws Exception {
        Stripper stripper;

        stripper = check(Empty.class, "main", String[].class);
        assertEquals(Arrays.asList(new ClassRef(Empty.class)), stripper.classes);
    }

    private Stripper check(Class<?> clazz, String method, Class<?> ... args) throws Exception {
        World world;
        Repository repo;
        Stripper stripper;
        MethodRef root;

        world = new World();
        repo = new Repository();
        repo.addAll(world.locateClasspathItem(getClass()));
        root = new MethodRef(clazz.getDeclaredMethod(method, args));
        stripper = new Stripper(repo);
        stripper.closure(root);
        return stripper;
    }
}
