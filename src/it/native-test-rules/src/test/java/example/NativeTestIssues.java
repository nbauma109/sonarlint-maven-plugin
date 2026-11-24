package example;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NativeTestIssues {
    @Test
    public void unnecessaryThrows() throws Exception {
        assertEquals(1, new Value().number());
    }

    @Test
    public void excessiveAssertions() {
        assertEquals(1, new Value().number() + 0);
        assertEquals(2, new Value().number() + 1);
        assertEquals(3, new Value().number() + 2);
        assertEquals(4, new Value().number() + 3);
        assertEquals(5, new Value().number() + 4);
        assertEquals(6, new Value().number() + 5);
        assertEquals(7, new Value().number() + 6);
        assertEquals(8, new Value().number() + 7);
        assertEquals(9, new Value().number() + 8);
        assertEquals(10, new Value().number() + 9);
        assertEquals(11, new Value().number() + 10);
        assertEquals(12, new Value().number() + 11);
        assertEquals(13, new Value().number() + 12);
        assertEquals(14, new Value().number() + 13);
        assertEquals(15, new Value().number() + 14);
        assertEquals(16, new Value().number() + 15);
        assertEquals(17, new Value().number() + 16);
        assertEquals(18, new Value().number() + 17);
        assertEquals(19, new Value().number() + 18);
        assertEquals(20, new Value().number() + 19);
        assertEquals(21, new Value().number() + 20);
        assertEquals(22, new Value().number() + 21);
        assertEquals(23, new Value().number() + 22);
        assertEquals(24, new Value().number() + 23);
        assertEquals(25, new Value().number() + 24);
        assertEquals(26, new Value().number() + 25);
    }

    @Test
    public void necessaryThrows() throws IOException {
        java.nio.file.Files.readString(java.nio.file.Path.of("missing"));
    }
}
