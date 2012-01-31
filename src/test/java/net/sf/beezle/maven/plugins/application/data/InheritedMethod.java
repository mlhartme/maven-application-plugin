package net.sf.beezle.maven.plugins.application.data;

public class InheritedMethod extends BaseMethod {
    public static void root() {
        new InheritedMethod().invoke();
    }

    public InheritedMethod() {
    }
}
