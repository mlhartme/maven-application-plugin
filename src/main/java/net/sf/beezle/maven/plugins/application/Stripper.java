package net.sf.beezle.maven.plugins.application;

import net.sf.beezle.mork.classfile.*;
import net.sf.beezle.sushi.archive.Archive;
import net.sf.beezle.sushi.fs.Node;
import net.sf.beezle.sushi.util.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Stripper {
    public static void run(Archive archive, String main) throws IOException {
        Repository repository;
        ClassDef c;
        MethodRef m;
        Stripper stripper;

        repository = new Repository();
        repository.addAllLazy(archive.data);
        m = new MethodRef(new ClassRef(main), false, ClassRef.VOID, "main", new ClassRef[] { new ClassRef(String[].class) });
        stripper = new Stripper(repository);
        stripper.closure(m);
        for (Node cf : archive.data.find("**/*.class")) {
            if (!stripper.referenced(cf.getRelative(archive.data))) {
                cf.delete();
            }
        }
    }

    private final Repository repository;
    private final List<MethodRef> methods;
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
        MethodDef next;
        Code code;

        add(root);
        // size grows!
        for (int i = 0; i < methods.size(); i++) {
            mr = methods.get(i);
            try {
                m = (MethodDef) mr.resolve(repository);
            } catch (ResolveException e) {
                if (mr.getOwner().name.equals("java.lang.Class") && mr.name.equals("forName")) {
                    System.out.println("CAUTION: " + mr);
                }
                if (!mr.getOwner().name.startsWith("java.")) {
                    System.out.println("not found: " + mr);
                }
                continue;
            }
            refs = new ArrayList<Reference>();
            code = m.getCode();
            if (code == null) {
                // TODO: abstract
            } else {
                code.references(refs);
                for (Reference ref : refs) {
                    if (ref instanceof MethodRef) {
                        add((MethodRef) ref);
                    } else {
                        // TODO
                    }
                }
            }
        }
    }

    public void add(MethodRef method) {
        if (!methods.contains(method)) {
            methods.add(method);
            add(method.getOwner());
        }
    }

    public void add(ClassRef clazz) {
        MethodRef ref;

        if (!classes.contains(clazz)) {
            classes.add(clazz);
            ref = new MethodRef(clazz, false, ClassRef.VOID, "<clinit>");
            try {
                ref.resolve(repository);
                add(ref);
            } catch (ResolveException e) {
                // no static initializer
                return;
            }
        }
    }

    public boolean referenced(String resourceName) {
        String name;

        name = Strings.removeRight(resourceName, ".class");
        name = name.replace('/', '.');
        return classes.contains(new ClassRef(name));
    }

    public int size() {
        return classes.size();
    }
}
