package net.sf.beezle.maven.plugins.application;

import net.sf.beezle.maven.plugins.application.data.Data;
import net.sf.beezle.mork.classfile.ClassRef;
import net.sf.beezle.mork.classfile.MethodRef;
import net.sf.beezle.mork.classfile.Repository;
import net.sf.beezle.sushi.fs.World;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class StripperTest {
    @Test
    public void simple() throws Exception {
        World world;
        Repository repo;
        Stripper stripper;

        world = new World();
        repo = new Repository();
        repo.addAll(world.locateClasspathItem(getClass()));
        stripper = new Stripper(repo);
        stripper.closure(new MethodRef(Data.class.getDeclaredMethod("empty", new Class<?>[0])));
        assertEquals(Arrays.asList(new ClassRef(Data.class)), stripper.classes);
    }
}
