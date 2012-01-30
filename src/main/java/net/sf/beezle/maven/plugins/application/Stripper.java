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
    private final List<ClassRef> classes;

    public Stripper(Repository repository) {
        this.repository = repository;
        this.classes = new ArrayList<ClassRef>();
    }

    public void closure(MethodRef root) {
        List<MethodDef> todo;
        MethodDef m;
        List<Reference> refs;
        MethodDef next;
        Code code;

        todo = new ArrayList<MethodDef>();
        try {
            todo.add((MethodDef) root.resolve(repository));
        } catch (ResolveException e) {
            throw new IllegalStateException(root.toString());
        }
        classes.add(root.getOwner());

        // size grows!
        for (int i = 0; i < todo.size(); i++) {
            m = todo.get(i);
            refs = new ArrayList<Reference>();
            code = m.getCode();
            if (code == null) {
                // TODO: abstract
            } else {
                code.references(refs);
                for (Reference ref : refs) {
                    if (ref instanceof MethodRef) {
                        try {
                            next = (MethodDef) ref.resolve(repository);
                            if (!todo.contains(next)) {
                                todo.add(next);
                                if (!classes.contains(ref.getOwner())) {
                                    classes.add(ref.getOwner());
                                }
                            }
                        } catch (ResolveException e) {
                            if (ref.getOwner().name.equals("java.lang.Class") && ((MethodRef) ref).name.equals("forName")) {
                                System.out.println(m + " uses " + ref);
                            }
                            if (!ref.getOwner().name.startsWith("java.")) {
                                System.out.println("not found: " + ref);
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean referenced(String resourceName) {
        String name;
        
        name = Strings.removeRight(resourceName, ".class");
        name = name.replace('/', '.');
        return classes.contains(new ClassRef(name));
    }
}
