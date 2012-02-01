package net.sf.beezle.maven.plugins.application;

import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ExceptionTable;
import javassist.bytecode.ExceptionsAttribute;
import javassist.bytecode.Opcode;
import net.sf.beezle.sushi.archive.Archive;
import net.sf.beezle.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** See also: http://java.sun.com/docs/books/jvms/second_edition/html/Concepts.doc.html#16491 */
public class Stripper {
    /** @param roots class.method names */
    public static Stripper run(Archive archive, List<String> roots) throws IOException {
        /*
        Repository repository;
        MethodRef m;
        Stripper stripper;

        repository = new Repository();
        repository.addAllLazy(archive.data);
        // TODO: Java classes outside of runtime.jar ...
        repository.addAllLazy(archive.data.getWorld().locateClasspathItem(Object.class));
        stripper = new Stripper(repository);
        for (String root : roots) {
            int idx;
            ClassRef ref;
            ClassDef def;

            idx = root.lastIndexOf('.');
            ref = new ClassRef(root.substring(0, idx));
            try {
                def = (ClassDef) ref.resolve(repository);
            } catch (ResolveException e) {
                throw new IllegalArgumentException("unknown class: " + ref.toString());
            }
            for (MethodDef method : def.methods) {
                if (method.name.equals(root.substring(idx + 1))) {
                    stripper.add(method.reference(ref, false));
                }
            }
        }
        stripper.closure();
        for (Node cf : archive.data.find("** /*.class")) {
            if (!stripper.referenced(cf.getRelative(archive.data))) {
                cf.delete();
            }
        }
        return stripper;*/ return null;
    }

    private final ClassPool pool;
    private final List<CtBehavior> methods;

    /** only classes in classpath */
    public final List<CtClass> classes;

    public Stripper(ClassPool pool) {
        this.pool = pool;
        this.methods = new ArrayList<CtBehavior>();
        this.classes = new ArrayList<CtClass>();

    }

    public void closure() {
        CtBehavior m;
        CodeAttribute code;
        CodeIterator iter;
        int idx;

        // size grows!
        for (int i = 0; i < methods.size(); i++) {
            m = methods.get(i);
            code = m.getMethodInfo().getCodeAttribute();
            iter = code.iterator();
            while (iter.hasNext()) {
                try {
                    idx = iter.next();
                } catch (BadBytecode e) {
                    throw new IllegalStateException(e);
                }
                switch (iter.byteAt(idx)) {
                    case Opcode.INVOKESPECIAL:
                    case Opcode.INVOKEVIRTUAL:
                    case Opcode.INVOKEINTERFACE:
                        // TODO
                        break;
                    // TODO: casts
                    // fields
                    default:
                        // ignores
                }

            }
        }
    }

    public void add(CtBehavior method) throws NotFoundException {
        ExceptionTable exceptions;
        ExceptionsAttribute ea;
        int size;

        if (!methods.contains(method)) {
            methods.add(method);
            add(method.getDeclaringClass());
            for (CtClass p : method.getParameterTypes()) {
                add(p);
            }
            if (method instanceof CtMethod) {
                add(((CtMethod) method).getReturnType());
            }
            exceptions = method.getMethodInfo().getCodeAttribute().getExceptionTable();
            size = exceptions.size();
            for (int i = 0; i < size; i++) {
                add(pool.get(method.getMethodInfo().getConstPool().getClassInfo(exceptions.catchType(i))));
            }
            ea = method.getMethodInfo().getExceptionsAttribute();
            if (ea != null) {
                // I've tested this: java loads exceptions declared via throws, even if they're never thrown!
                for (String exception : ea.getExceptions()) {
                    add(pool.get(exception));
                }
            }
            if (method instanceof CtMethod) {
                for (CtMethod derived : derived((CtMethod) method)) {
                    add(derived);
                }
            }
        }
    }

    private boolean contains(Object[] objects, Object element) {
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
        CtMethod derivedMethod;

        result = new ArrayList<CtMethod>();
        baseClass = baseMethod.getDeclaringClass();
        for (CtClass derivedClass : classes) {
            if (baseClass.equals(derivedClass.getSuperclass()) || contains(derivedClass.getInterfaces(), baseClass)) {
                derivedMethod = derivedClass.getDeclaredMethod(baseMethod.getName(), baseMethod.getParameterTypes());
                if (derivedMethod != null) {
                    result.add(derivedMethod);
                }
            }
        }
        return result;
    }

    public void add(CtClass clazz) throws NotFoundException {
        if (!classes.contains(clazz)) {
            classes.add(clazz);
            if (clazz.getSuperclass() != null) {
                add(clazz.getSuperclass());
            }
            for (CtBehavior method : clazz.getDeclaredBehaviors()) {
                if (method.getName().equals("<clinit>")) {
                    add(method);
                }
            }
            for (CtMethod derived : clazz.getDeclaredMethods()) {
                for (CtBehavior base : new ArrayList<CtBehavior>(methods)) { // TODO: copy ...
                    if (base instanceof CtMethod) {
                        if (overrides((CtMethod) base, derived)) {
                            add(derived);
                        }
                    }
                }
            }
        }
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

        for (CtBehavior m : methods) {
            name = m.getDeclaringClass().getName();
            if (name.startsWith("java.lang.reflect.")) {
                System.out.println("CAUTION: " + m);
            }
            if (name.equals("java.lang.Class") && m.getName().equals("forName")) {
                System.out.println("CAUTION: " + m);
            }
            if (name.equals("java.lang.ClassLoader") && m.getName().equals("loadClass")) {
                System.out.println("CAUTION: " + m);
            }
        }
    }

    public ClassPool getPool() {
        return pool;
    }
}
