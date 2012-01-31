package net.sf.beezle.maven.plugins.application.data;

public class BaseMethod {
    public static void root() {
        new BaseMethod().invoke();
    }

    public BaseMethod() {
    }

    public void invoke() {
        Used.ping();
    }
}
