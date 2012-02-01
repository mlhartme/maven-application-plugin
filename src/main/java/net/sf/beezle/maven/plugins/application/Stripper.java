package net.sf.beezle.maven.plugins.application;

import net.sf.beezle.mork.classfile.*;
import net.sf.beezle.mork.classfile.attribute.Attribute;
import net.sf.beezle.sushi.archive.Archive;
import net.sf.beezle.sushi.fs.Node;
import net.sf.beezle.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** See also: http://java.sun.com/docs/books/jvms/second_edition/html/Concepts.doc.html#16491 */
public class Stripper {
    public static Stripper run(Archive archive, String main, List<String> dynamitcReferences) throws IOException {
        Repository repository;
        ClassDef c;
        MethodRef m;
        Stripper stripper;

        repository = new Repository();
        repository.addAllLazy(archive.data);
        // TODO: Java classes outside of runtime.jar ...
        repository.addAllLazy(archive.data.getWorld().locateClasspathItem(Object.class));
        m = new MethodRef(new ClassRef(main), false, ClassRef.VOID, "main", new ClassRef[] { new ClassRef(String[].class) });
        stripper = new Stripper(repository);
        for (String dynamic : dynamitcReferences) {
            int idx;
            ClassRef ref;
            ClassDef def;

            idx = dynamic.lastIndexOf('.');
            ref = new ClassRef(dynamic.substring(0, idx));
            try {
                def = (ClassDef) ref.resolve(repository);
            } catch (ResolveException e) {
                throw new IllegalArgumentException();
            }
            for (MethodDef method : def.methods) {
                if (method.name.equals(dynamic.substring(idx + 1))) {
                    stripper.add(method.reference(ref, false));
                }
            }
        }
        stripper.closure(m);
        for (Node cf : archive.data.find("**/*.class")) {
            if (!stripper.referenced(cf.getRelative(archive.data))) {
                cf.delete();
            }
        }
        return stripper;
    }

    private final Repository repository;
    private final List<MethodRef> methods;

    /** only classes in classpath */
    public final List<ClassRef> classes;

    public Stripper(Repository repository) {
        this.repository = repository;
        this.methods = new ArrayList<MethodRef>();
        this.classes = new ArrayList<ClassRef>();

    }

    public void closure(MethodRef root) {
        MethodRef mr;
        MethodDef m;
        List<Reference> refs;
        Code code;

        add(root);
        // size grows!
        for (int i = 0; i < methods.size(); i++) {
            mr = methods.get(i);
            try {
                m = (MethodDef) mr.resolve(repository);
            } catch (ResolveException e) {
                // TODO:
                // Object.<init>: System.out.println("not found: " + mr);

                // TODO
                continue;
            }
            refs = new ArrayList<Reference>();
            code = m.getCode();
            if (code == null) {
                // abstract
            } else {
                code.references(refs);
                for (Reference ref : refs) {
                    if (ref instanceof MethodRef) {
                        add((MethodRef) ref);
                    } else if (ref instanceof ClassRef) {
                        // array-new
                        add((ClassRef) ref);
                    } else if (ref instanceof FieldRef) {
                        // 2.17.4 (initialization) and 2.17.6 (creation of new instances)
                        try {
                            FieldDef field = (FieldDef) ref.resolve(repository);
                            add(field.type);
                        } catch (ResolveException e) {
                            // TODO
                        }
                    } else {
                        throw new IllegalStateException(ref.toString());
                    }
                }
            }
        }
    }

    public void add(MethodRef method) {
        if (!methods.contains(method)) {
            methods.add(method);
            add(method.getOwner());
            for (ClassRef arg : method.argumentTypes) {
                add(arg);
            }
            add(method.returnType);
            // I've tested this: java loads exceptions declared via throws, even if they're never thrown!
            addExceptions(method);
            for (MethodRef derived : derived(method)) {
                add(derived);
            }
        }
    }
    public void addExceptions(MethodRef method) {
        MethodDef def;

        try {
            def = (MethodDef) method.resolve(repository);
        } catch (ResolveException e) {
            // TODO
            return;
        }
        for (ClassRef ex : def.getExceptions()) {
            add(ex);
        }
    }

    /** @return methodRefs to already visited classes that directly override baseMethod */
    public List<MethodRef> derived(MethodRef baseMethod) {
        List<MethodRef> result;
        ClassRef baseClass;
        ClassDef derivedClass;

        result = new ArrayList<MethodRef>();
        baseClass = baseMethod.getOwner();
        for (ClassRef c : classes) {
            try {
                derivedClass = (ClassDef) c.resolve(repository);
            } catch (ResolveException e) {
                // TODO
                continue;
            }
            if (baseClass.equals(derivedClass.superClass) || derivedClass.interfaces.contains(baseClass)) {
                for (MethodDef derivedMethod : derivedClass.methods) {
                    if (sameSignature(baseMethod, derivedMethod)) {
                        result.add(derivedMethod.reference(c, derivedClass.accessFlags.contains(Access.INTERFACE)));
                    }
                }
            }
        }
        return result;
    }

    public void add(ClassRef clazz) {
        ClassDef def;
        MethodDef clinit;
        MethodRef derived;

        if (!classes.contains(clazz)) {
            try {
                // don't try to resolve a method.ref, because it might resolve to a base class initializer
                def = (ClassDef) clazz.resolve(repository);
            } catch (ResolveException e) {
                // not in classpath
                return;
            }
            classes.add(clazz);
            if (def.superClass != null) {
                add(def.superClass);
            }
            clinit = def.lookupMethod("<clinit>");
            if (clinit != null) {
                add(new MethodRef(clazz, false, ClassRef.VOID, clinit.name));
            }
            for (MethodDef method : def.methods) {
                derived = method.reference(clazz, def.accessFlags.contains(Access.INTERFACE));
                for (MethodRef base : new ArrayList<MethodRef>(methods)) { // TODO: copy ...
                    if (overrides(base, def, derived)) {
                        add(derived);
                    }
                }
            }
        }
    }

    private boolean overrides(MethodRef base, ClassDef derivedClass, MethodRef derivedMethod) {
        if (base.getOwner().equals(derivedClass.superClass) || derivedClass.interfaces.contains(base.getOwner())) {
            return sameSignature(base, derivedMethod);
        }
        return false;
    }

    private boolean sameSignature(MethodRef left, MethodRef right) {
        if (left.argumentTypes.length == right.argumentTypes.length) {
            if (left.name.equals(right.name)) {
                // the return type is not checked - it doesn't matter!

                for (int i = 0; i < left.argumentTypes.length; i++) {
                    if (!left.argumentTypes[i].equals(right.argumentTypes[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean sameSignature(MethodRef left, MethodDef right) {
        if (left.argumentTypes.length == right.argumentTypes.length) {
            if (left.name.equals(right.name)) {
                // the return type is not checked - it doesn't matter!

                for (int i = 0; i < left.argumentTypes.length; i++) {
                    if (!left.argumentTypes[i].equals(right.argumentTypes[i])) {
                        return false;
                    }
                }
                return true;
            }
        }
        return false;
    }

    public boolean referenced(String resourceName) {
        String name;

        name = Strings.removeRight(resourceName, ".class");
        name = name.replace('/', '.');
        return classes.contains(new ClassRef(name));
    }

    public void warnings() {
        for (MethodRef mr : methods) {
            if (mr.getOwner().name.startsWith("java.lang.reflect.")) {
                System.out.println("CAUTION: " + mr);
            }
            if (mr.getOwner().name.equals("java.lang.Class") && mr.name.equals("forName")) {
                System.out.println("CAUTION: " + mr);
            }
            if (mr.getOwner().name.equals("java.lang.ClassLoader") && mr.name.equals("loadClass")) {
                System.out.println("CAUTION: " + mr);
            }
        }
    }
}