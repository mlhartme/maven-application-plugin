package net.sf.beezle.maven.plugins.application.data;

public class Base {
    public static Base create() {
        return new Base();
    }

    public Base() {
        Used.ping();
    }
}
