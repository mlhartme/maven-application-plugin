package net.sf.beezle.maven.plugins.application;

import net.sf.beezle.maven.plugins.application.data.Empty;
import net.sf.beezle.maven.plugins.application.data.Normal;
import net.sf.beezle.maven.plugins.application.data.StaticInit;
import net.sf.beezle.maven.plugins.application.data.Used;
import net.sf.beezle.mork.classfile.ClassRef;
import net.sf.beezle.mork.classfile.MethodRef;
import net.sf.beezle.mork.classfile.Repository;
import net.sf.beezle.sushi.fs.World;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class StripperTest {
    @Test
    public void empty() throws Exception {
        expected(check(Empty.class, "main", String[].class), Empty.class);
    }

    @Test
    public void normal() throws Exception {
        expected(check(Normal.class, "foo"), Normal.class, Used.class);
    }

    @Test
    public void staticInit() throws Exception {
        expected(check(StaticInit.class, "foo"), StaticInit.class, Used.class);
    }

    //--

    private void expected(Stripper stripper, Class<?> ... classes) {
        List<ClassRef> expected;

        expected = new ArrayList<ClassRef>();
        for (Class<?> c : classes) {
            expected.add(new ClassRef(c));
        }
        assertEquals(expected, stripper.classes);
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
