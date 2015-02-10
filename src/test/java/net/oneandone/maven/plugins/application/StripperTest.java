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

import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import net.oneandone.maven.plugins.application.data.*;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Ignore // TODO: because it complains about java 8 bytecodes
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

        expected = new ArrayList<>();
        for (Class<?> c : classes) {
            expected.add(c.getName());
        }
        actual = new ArrayList<>();
        for (CtClass c : stripper.classes) {
            if (c.getName().startsWith("java.") || c.getName().startsWith("sun.") || c.getName().startsWith("com.sun.")
                    || c.getName().startsWith("javax.") || c.isPrimitive() || c.isArray()) {
                //
            } else {
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

        pool = new ClassPool();
        pool.appendClassPath(new ClassClassPath(this.getClass()));
        cc = pool.get(clazz.getName());
        root = cc.getDeclaredMethod(method);
        stripper = new Stripper(pool);
        stripper.add(root);
        stripper.closure();
        return stripper;
    }
}
