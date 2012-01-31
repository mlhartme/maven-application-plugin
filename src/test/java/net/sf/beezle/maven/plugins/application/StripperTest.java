package net.sf.beezle.maven.plugins.application;

import net.sf.beezle.maven.plugins.application.data.Array;
import net.sf.beezle.maven.plugins.application.data.BaseConstructor;
import net.sf.beezle.maven.plugins.application.data.BaseMethod;
import net.sf.beezle.maven.plugins.application.data.Empty;
import net.sf.beezle.maven.plugins.application.data.Field;
import net.sf.beezle.maven.plugins.application.data.Ifc;
import net.sf.beezle.maven.plugins.application.data.Impl;
import net.sf.beezle.maven.plugins.application.data.Impl2;
import net.sf.beezle.maven.plugins.application.data.InheritedConstructor;
import net.sf.beezle.maven.plugins.application.data.InheritedMethod;
import net.sf.beezle.maven.plugins.application.data.InheritedStaticInit;
import net.sf.beezle.maven.plugins.application.data.Normal;
import net.sf.beezle.maven.plugins.application.data.StaticInit;
import net.sf.beezle.maven.plugins.application.data.Used;
import net.sf.beezle.maven.plugins.application.data.Used2;
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

    @Test
    public void inheritedStaticInit() throws Exception {
        expected(check(InheritedStaticInit.class, "foo"), InheritedStaticInit.class, StaticInit.class, Used.class);
    }

    @Test
    public void constructor() throws Exception {
        expected(check(BaseConstructor.class, "create"), BaseConstructor.class, Used.class);
    }

    @Test
    public void inheritedConstructor() throws Exception {
        expected(check(InheritedConstructor.class, "create"), InheritedConstructor.class, BaseConstructor.class, Used.class);
    }

    @Test
    public void baseMethod() throws Exception {
        expected(check(BaseMethod.class, "root"), BaseMethod.class, Used.class);
    }

    @Test
    public void inheritedMethod() throws Exception {
        expected(check(InheritedMethod.class, "root"), InheritedMethod.class, BaseMethod.class, Used.class);
    }

    @Test
    public void ifc() throws Exception {
        expected(check(Impl.class, "run"), Impl.class, Ifc.class, Impl2.class, Used.class, Used2.class);
    }

    @Test
    public void fieldNormal() throws Exception {
        expected(check(Field.class, "useNormal"), Field.class, Used2.class);
    }

    @Test
    public void fieldStatic() throws Exception {
        expected(check(Field.class, "useStatic"), Field.class, Used.class);
    }

    @Test
    public void array() throws Exception {
        expected(check(Array.class, "create"), Array.class, Used.class);
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
