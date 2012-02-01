package net.sf.beezle.maven.plugins.application.data;

public class Normal {
    public static void foo() {
        Used.ping();
    }

    public static void argument() {
        argumentHelper(null);
    }
    public static void argumentHelper(Used used) {
    }

    public static Used result() {
        return null;
    }

    public static void exception() throws Ex {
    }

    public static Object cast() {
        Object obj = null;
        return (Used) obj;
    }

    public static void variable() {
        Used u = null;
    }
}
