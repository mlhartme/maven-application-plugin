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
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.ExceptionTable;
import javassist.bytecode.ExceptionsAttribute;
import javassist.bytecode.Opcode;
import net.oneandone.sushi.archive.Archive;
import net.oneandone.sushi.fs.Node;
import net.oneandone.sushi.util.Strings;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** See also: http://java.sun.com/docs/books/jvms/second_edition/html/Concepts.doc.html#16491 */
public class Stripper {
    /** @param roots class.method names */
    public static Stripper run(final Archive archive, List<String> roots) throws IOException, NotFoundException {
        ClassPool pool;
        Stripper stripper;

        pool = new ClassPool();
        pool.appendClassPath(new ClassClassPath(Object.class));
        pool.appendClassPath(new ClassPath() {
            @Override
            public InputStream openClassfile(String classname) throws NotFoundException {
                try {
                    return node(classname).createInputStream();
                } catch (IOException e) {
                    throw new NotFoundException(classname, e);
                }
            }

            @Override
            public URL find(String classname) {
                // TODO: node.getUri.toURL() complains about unknown protocol; over, ClassPool only the results whether it's != null
                try {
                    return new URL("file:///" + node(classname).getPath());
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public void close() {
            }

            private Node node(String classname) {
                return archive.data.join(classname.replace('.', '/') + ".class");
            }
        });

        stripper = new Stripper(pool);
        for (String root : roots) {
            int idx;
            CtClass def;

            idx = root.lastIndexOf('.');
            def = pool.get(root.substring(0, idx));
            for (CtMethod method : def.getDeclaredMethods()) {
                if (method.getName().equals(root.substring(idx + 1))) {
                    stripper.add(method);
                }
            }
        }
        stripper.closure();
        for (Node cf : archive.data.find("**/*.class")) {
            if (!stripper.referenced(cf.getRelative(archive.data))) {
                cf.deleteFile();
            }
        }
        return stripper;
    }

    private final ClassPool pool;

    /** reachable code */
    private final List<CtBehavior> behaviors;

    /** only classes in classpath */
    public final List<CtClass> classes;

    public Stripper(ClassPool pool) {
        this.pool = pool;
        this.behaviors = new ArrayList<CtBehavior>();
        this.classes = new ArrayList<CtClass>();

    }

    public void closure() throws NotFoundException {
        CodeAttribute code;
        CodeIterator iter;
        int pos;
        ConstPool pool;
        int index;
        CtClass clazz;
        CtBehavior b;

        // size grows!
        for (int i = 0; i < behaviors.size(); i++) {
            code = behaviors.get(i).getMethodInfo().getCodeAttribute();
            if (code == null) {
                continue;
            }
            pool = code.getConstPool();
            iter = code.iterator();
            while (iter.hasNext()) {
                try {
                    pos = iter.next();
                } catch (BadBytecode e) {
                    throw new IllegalStateException(e);
                }
                switch (iter.byteAt(pos)) {
                    case Opcode.GETSTATIC:
                    case Opcode.PUTSTATIC:
                    case Opcode.GETFIELD:
                    case Opcode.PUTFIELD:
                        add(Descriptor.toCtClass(pool.getFieldrefType(iter.u16bitAt(pos + 1)), this.pool));
                        break;
                    case Opcode.INVOKEVIRTUAL:
                    case Opcode.INVOKESTATIC:
                    case Opcode.INVOKESPECIAL:
                        index = iter.u16bitAt(pos + 1);
                        clazz = Descriptor.toCtClass(pool.getMethodrefClassName(index), this.pool);
                        try {
                            b = clazz.getMethod(pool.getMethodrefName(index), pool.getMethodrefType(index));
                        } catch (NotFoundException e) {
                            b = clazz.getConstructor(pool.getMethodrefType(index));
                        }
                        add(b);
                        break;
                    case Opcode.INVOKEINTERFACE:
                        index = iter.u16bitAt(pos + 1);
                        clazz = Descriptor.toCtClass(pool.getInterfaceMethodrefClassName(index), this.pool);
                        add(clazz.getMethod(pool.getInterfaceMethodrefName(index), pool.getInterfaceMethodrefType(index)));
                        break;
                    case Opcode.ANEWARRAY:
                    case Opcode.CHECKCAST:
                    case Opcode.MULTIANEWARRAY:
                    case Opcode.NEW:
                        add(this.pool.getCtClass(pool.getClassInfo(iter.u16bitAt(pos + 1))));
                        break;
                    default:
                        // nothing
                }
            }
        }
    }

    /** method or constructor is reachable */
    public void add(CtBehavior behavior) throws NotFoundException {
        ExceptionTable exceptions;
        ExceptionsAttribute ea;
        int size;
        CodeAttribute code;

        if (!contains(behaviors, behavior)) {
            add(behavior.getDeclaringClass());
            behaviors.add(behavior);
            for (CtClass p : behavior.getParameterTypes()) {
                add(p);
            }
            if (behavior instanceof CtMethod) {
                add(((CtMethod) behavior).getReturnType());
            }
            code = behavior.getMethodInfo().getCodeAttribute();
            if (code != null) {
                exceptions = code.getExceptionTable();
                size = exceptions.size();
                for (int i = 0; i < size; i++) {
                    String name = behavior.getMethodInfo().getConstPool().getClassInfo(exceptions.catchType(i));
                    if (name != null) {
                        add(pool.get(name));
                    }
                }
            }
            ea = behavior.getMethodInfo().getExceptionsAttribute();
            if (ea != null) {
                // I've tested this: java loads exceptions declared via throws, even if they're never thrown!
                for (String exception : ea.getExceptions()) {
                    add(pool.get(exception));
                }
            }
            if (behavior instanceof CtMethod) {
                for (CtMethod derived : derived((CtMethod) behavior)) {
                    add(derived);
                }
            }
        }
    }

    public void add(CtClass clazz) throws NotFoundException {
        if (!classes.contains(clazz)) {
            classes.add(clazz);
            if (clazz.getSuperclass() != null) {
                add(clazz.getSuperclass());
            }
            for (CtBehavior behavior : clazz.getDeclaredBehaviors()) {
                if (behavior.getName().equals("<clinit>")) {
                    add(behavior);
                }
            }
            for (CtMethod derived : clazz.getDeclaredMethods()) {
                for (CtBehavior base : new ArrayList<CtBehavior>(behaviors)) { // TODO: copy ...
                    if (base instanceof CtMethod) {
                        if (overrides((CtMethod) base, derived)) {
                            add(derived);
                        }
                    }
                }
            }
        }
    }


    /** CtBehavior.equals compare method name and arguments only ... */
    private static boolean contains(List<CtBehavior> lst, CtBehavior right) {
        for (CtBehavior left : lst) {
            if (left.equals(right)) {
                if (left.getDeclaringClass().equals(right.getDeclaringClass())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(Object[] objects, Object element) {
        for (Object obj : objects) {
            if (obj.equals(element)) {
                return true;
            }
        }
        return false;
    }

    /** @return methodRefs to already visited classes that directly override baseMethod */
    public List<CtMethod> derived(CtMethod baseMethod) throws NotFoundException {
        List<CtMethod> result;
        CtClass baseClass;

        result = new ArrayList<CtMethod>();
        baseClass = baseMethod.getDeclaringClass();
        for (CtClass derivedClass : classes) {
            if (baseClass.equals(derivedClass.getSuperclass()) || contains(derivedClass.getInterfaces(), baseClass)) {
                try {
                    result.add(derivedClass.getDeclaredMethod(baseMethod.getName(), baseMethod.getParameterTypes()));
                } catch (NotFoundException e) {
                    // nothing to do
                }
            }
        }
        return result;
    }

    private boolean overrides(CtMethod base, CtMethod derivedMethod) throws NotFoundException {
        CtClass baseClass;
        CtClass derivedClass;

        baseClass = base.getDeclaringClass();
        derivedClass = derivedMethod.getDeclaringClass();
        if (baseClass.equals(derivedClass.getSuperclass()) || contains(derivedClass.getInterfaces(), baseClass)) {
            return sameSignature(base, derivedMethod);
        }
        return false;
    }

    private boolean sameSignature(CtMethod left, CtMethod right) throws NotFoundException {
        CtClass[] leftParams;
        CtClass[] rightParams;

        if (left.getName().equals(right.getName())) {
            leftParams = left.getParameterTypes();
            rightParams = right.getParameterTypes();
            if (left.getParameterTypes().length == right.getParameterTypes().length) {
                // the return type is not checked - it doesn't matter!

                for (int i = 0; i < left.getParameterTypes().length; i++) {
                    if (!leftParams[i].equals(rightParams[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public boolean referenced(String resourceName) throws NotFoundException {
        String name;

        name = Strings.removeRight(resourceName, ".class");
        name = name.replace('/', '.');
        return classes.contains(pool.get(name));
    }

    public void warnings() {
        String name;

        for (CtBehavior b : behaviors) {
            name = b.getDeclaringClass().getName();
            if (name.startsWith("java.lang.reflect.")) {
                System.out.println("CAUTION: " + b);
            }
            if (name.equals("java.lang.Class") && b.getName().equals("forName")) {
                System.out.println("CAUTION: " + b);
            }
            if (name.equals("java.lang.ClassLoader") && b.getName().equals("loadClass")) {
                System.out.println("CAUTION: " + b);
            }
        }
    }
}
