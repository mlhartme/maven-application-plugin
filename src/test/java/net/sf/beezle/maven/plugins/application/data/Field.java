package net.sf.beezle.maven.plugins.application.data;

public class Field {
    public static Used foo;
    public Used2 normal;

    public void useNormal() {
        normal = null;
    }

    public static void useStatic() {
        foo = null;
    }
}
