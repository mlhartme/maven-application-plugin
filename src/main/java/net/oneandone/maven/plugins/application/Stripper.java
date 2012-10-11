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

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
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
import net.oneandone.sushi.fs.file.FileNode;
import net.oneandone.sushi.util.Strings;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** See also: http://java.sun.com/docs/books/jvms/second_edition/html/Concepts.doc.html#16491 */
public class Stripper {
    /** @param roots class names or class.method names */
    public static Stripper run(final Archive archive, List<String> roots, FileNode log) throws IOException, NotFoundException {
        ClassPool pool;
        Stripper stripper;

        pool = new ClassPool();
        pool.appendClassPath(new ClassClassPath(Object.class));
        pool.appendClassPath(new ArchiveClassPath(archive));

        stripper = new Stripper(pool);
        for (String root : roots) {
            int idx;
            CtClass def;

            def = pool.getOrNull(root);
            if (def != null) {
                for (CtBehavior behavior : def.getDeclaredBehaviors()) {
                    stripper.add(behavior);
                }
            } else {
                idx = root.lastIndexOf('.');
                def = pool.get(root.substring(0, idx));
                for (CtMethod method : def.getDeclaredMethods()) {
                    if (method.getName().equals(root.substring(idx + 1))) {
                        stripper.add(method);
                    }
                }
            }
        }
        stripper.closure();
        try (Writer logWriter = log.createWriter()) {
            List<Node> deletes; // defer until log output is complete

            deletes = new ArrayList<>();
            for (Node cf : archive.data.find("**/*.class")) {
                CtClass c;
                List<String> modified;

                c = stripper.reference(cf.getRelative(archive.data));
                if (c == null) {
                    logWriter.write("- ");
                    logWriter.write(className(cf.getRelative(archive.data)));
                    logWriter.write('\n');
                    deletes.add(cf);
                } else {
                    modified = new ArrayList<>();
                    for (CtField field : c.getDeclaredFields()) {
                        if (!stripper.fields.contains(field)) {
                            modified.add(field.getType().getName() + " " + field.getName());
                            c.removeField(field);
                        }
                    }
                    for (CtBehavior behavior : c.getDeclaredBehaviors()) {
                        if (!contains(stripper.behaviors, behavior)) {
                            if (behavior instanceof CtConstructor) {
                                modified.add(behavior.getLongName());
                                c.removeConstructor((CtConstructor) behavior);
                            } else {
                                modified.add(((CtMethod) behavior).getReturnType().getName() + " " + behavior.getLongName());
                                c.removeMethod((CtMethod) behavior);
                            }
                        }
                    }
                    if (!modified.isEmpty()) {
                        try {
                            c.writeFile();
                        } catch (CannotCompileException e) {
                            throw new IllegalStateException(e);
                        }
                        logWriter.write("* ");
                        logWriter.write(c.getName());
                        logWriter.write('\n');
                        for (String line : modified) {
                            logWriter.write("  - ");
                            logWriter.write(line);
                            logWriter.write('\n');
                        }
                    }
                }
            }
            for (Node node : deletes) {
                node.deleteFile();
            }
        }
        return stripper;
    }

    private final ClassPool classPool;

    /** reachable code */
    private final List<CtBehavior> behaviors;

    /** only classes in classpath, no java runtime classes */
    public final List<CtClass> classes;

    public final List<CtField> fields;

    public Stripper(ClassPool classPool) {
        this.classPool = classPool;
        this.behaviors = new ArrayList<>();
        this.classes = new ArrayList<>();
        this.fields = new ArrayList<>();
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
                        index = iter.u16bitAt(pos + 1);
                        clazz = Descriptor.toCtClass(pool.getFieldrefClassName(index), this.classPool);
                        add(clazz.getField(pool.getFieldrefName(index)));
                        break;
                    case Opcode.INVOKEVIRTUAL:
                    case Opcode.INVOKESTATIC:
                    case Opcode.INVOKESPECIAL:
                        index = iter.u16bitAt(pos + 1);
                        clazz = Descriptor.toCtClass(pool.getMethodrefClassName(index), this.classPool);
                        try {
                            b = clazz.getMethod(pool.getMethodrefName(index), pool.getMethodrefType(index));
                        } catch (NotFoundException e) {
                            b = clazz.getConstructor(pool.getMethodrefType(index));
                        }
                        add(b);
                        break;
                    case Opcode.INVOKEINTERFACE:
                        index = iter.u16bitAt(pos + 1);
                        clazz = Descriptor.toCtClass(pool.getInterfaceMethodrefClassName(index), this.classPool);
                        add(clazz.getMethod(pool.getInterfaceMethodrefName(index), pool.getInterfaceMethodrefType(index)));
                        break;
                    case Opcode.ANEWARRAY:
                    case Opcode.CHECKCAST:
                    case Opcode.MULTIANEWARRAY:
                    case Opcode.NEW:
                        add(this.classPool.getCtClass(pool.getClassInfo(iter.u16bitAt(pos + 1))));
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
                        add(classPool.get(name));
                    }
                }
            }
            ea = behavior.getMethodInfo().getExceptionsAttribute();
            if (ea != null) {
                // I've tested this: java loads exceptions declared via throws, even if they're never thrown!
                for (String exception : ea.getExceptions()) {
                    add(classPool.get(exception));
                }
            }
            if (behavior instanceof CtMethod) {
                for (CtMethod derived : derived((CtMethod) behavior)) {
                    add(derived);
                }
            }
        }
    }

    public void add(CtField field) throws NotFoundException {
        if (!fields.contains(field)) {
            fields.add(field);
            add(field.getDeclaringClass());
            add(field.getType());
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
                for (CtBehavior base : new ArrayList<>(behaviors)) { // TODO: copy ...
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

    public CtClass reference(String resourceName) throws NotFoundException {
        CtClass clazz;

        clazz = classPool.get(className(resourceName));
        return classes.contains(clazz) ? clazz : null;
    }

    public static String className(String resourceName) {
        return Strings.removeRight(resourceName, ".class").replace('/', '.');
    }

    public static final List<String> SAVE = Arrays.asList(
            "sun.misc.Unsafe", "sun.reflect.ConstantPool", "sun.misc.VM",
            "java.io.Console", "java.io.FileSystem", "java.io.ObjectStreamClass",
            "java.lang.Double", "java.lang.Object", "java.lang.Package", "java.lang.System", "java.lang.ClassLoader",
            "java.lang.Thread", "java.langg.Throwable", "java.lang.SecurityManager",
            "java.net.InetAddress", "java.net.NetworkingInterface",
            "java.security.AccessController",
            "java.util.TimeZone");

    public void warnings(Log log) {
        /// Not that you cannot apply native to fields or classes
        for (CtBehavior b : behaviors) {
            if ((b.getModifiers() & Modifier.NATIVE) != 0) {
                if (!SAVE.contains(b.getDeclaringClass().getName())) {
                    log.warn("native method: " + b.getLongName());
                }
            }
        }
    }

}
