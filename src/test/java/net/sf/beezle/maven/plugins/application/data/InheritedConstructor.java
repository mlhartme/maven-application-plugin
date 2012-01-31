package net.sf.beezle.maven.plugins.application.data;

public class InheritedConstructor extends BaseConstructor {
    public static InheritedConstructor create() {
        return new InheritedConstructor();
    }
}
