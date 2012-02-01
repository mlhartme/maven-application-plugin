package net.sf.beezle.maven.plugins.application;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import net.sf.beezle.maven.plugins.application.data.*;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class StripperTest {
    @Test
    public void empty() throws Exception {
        expected(check(Empty.class, "main"), Empty.class);
    }

    @Test
    public void normal() throws Exception {
        expected(check(Normal.class, "foo"), Normal.class, Used.class);
        expected(check(Normal.class, "argument"), Normal.class, Used.class);
        expected(check(Normal.class, "result"), Normal.class, Used.class);
        expected(check(Normal.class, "exception"), Normal.class, Ex.class);
    }

    @Test
    public void ctch() throws Exception {
        expected(check(Normal.class, "catchBuiltIn"), Normal.class);
        expected(check(Normal.class, "catchRex"), Normal.class, Rex.class);
    }

    @Test
    public void cast() throws Exception {
        expected(check(Normal.class, "cast"), Normal.class, Used.class);
    }

    @Test
    public void variable() throws Exception {
        expected(check(Normal.class, "variable"), Normal.class /* Not: Used.class */);
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
        List<String> expected;
        List<String> actual;

        expected = new ArrayList<String>();
        for (Class<?> c : classes) {
            expected.add(c.getName());
        }
        actual = new ArrayList<String>();
        for (CtClass c : stripper.classes) {
            if (!c.getName().startsWith("java.") && !c.isPrimitive()) {
                actual.add(c.getName());
            }
        }
        assertEquals(expected, actual);
    }

    private Stripper check(Class<?> clazz, String method) throws Exception {
        ClassPool pool;
        CtClass cc;
        Stripper stripper;
        CtMethod root;

        pool = ClassPool.getDefault();
        cc = pool.get(clazz.getName());
        root = cc.getDeclaredMethod(method);
        stripper = new Stripper(pool);
        stripper.add(root);
        stripper.closure();
        return stripper;
    }
}
