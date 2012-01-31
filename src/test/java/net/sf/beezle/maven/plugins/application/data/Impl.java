package net.sf.beezle.maven.plugins.application.data;

public class Impl implements Ifc {
    public static void run() {
        Ifc i = new Impl();
        i.invoke();
        new Impl2();
    }

    public void invoke() {
        Used.ping();
    }
}
