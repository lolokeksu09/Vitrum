package app.rayclient.lite;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiteGenerationTest {
    @Test public void startStopStartInvalidatesPriorWorkers() {
        LiteGeneration gen = new LiteGeneration();
        int first = gen.start();
        assertTrue(gen.current(first));
        gen.cancel();
        assertFalse(gen.current(first));
        int second = gen.start();
        assertFalse(gen.current(first));
        assertTrue(gen.current(second));
        gen.cancel();
        assertFalse(gen.current(second));
    }
    @Test public void repeatedStopKeepsInvalidating() {
        LiteGeneration gen = new LiteGeneration();
        int first = gen.start();
        gen.cancel();
        gen.cancel();
        assertFalse(gen.current(first));
        assertTrue(gen.current(gen.start()));
    }
}
