package net.sf.beezle.maven.plugins.application.data;

public class BaseConstructor {
    public static BaseConstructor create() {
        return new BaseConstructor();
    }

    public BaseConstructor() {
        Used.ping();
    }
}
