package lang.ktav;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeByteInputTest {

    @BeforeAll
    static void requireBuiltCabi() {
        TestPaths.init();
        String debugFlag = System.getenv("KTAV_DEBUG_CABI");
        if (debugFlag != null && debugFlag.matches("(?i)debug")) {
            String libPath = System.getenv("KTAV_LIB_PATH");
            assertTrue(libPath != null && Files.isRegularFile(Paths.get(libPath)),
                    CorpusHarness.CABI_MISSING + " (KTAV_LIB_PATH=" + libPath + ")");
        } else {
            assertTrue(TestPaths.cabiBuilt(), CorpusHarness.CABI_MISSING);
        }
    }

    @Test
    void byteAndStringInputsAgreeForValidUtf8() {
        String src = "a: 1\n";
        assertEquals(Ktav.loads(src), Ktav.loads(src.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(Ktav.loadsStrict(src),
                Ktav.loadsStrict(src.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test
    void invalidUtf8HasNonEmptySourceSpan() {
        byte[] raw = {(byte) 0xff};
        KtavException lax = assertThrows(KtavException.class, () -> Ktav.loads(raw));
        assertEquals("InvalidUtf8", lax.getError());
        assertNotNull(lax.getSpanStart());
        assertNotNull(lax.getSpanEnd());
        assertTrue(lax.getSpanEnd() > lax.getSpanStart());

        KtavException strict = assertThrows(KtavException.class, () -> Ktav.loadsStrict(raw));
        assertEquals("InvalidUtf8", strict.getError());
        assertNotNull(strict.getSpanStart());
        assertNotNull(strict.getSpanEnd());
        assertTrue(strict.getSpanEnd() > strict.getSpanStart());
    }

    @Test
    void byteInputsPreserveEmptyLiveBufferPath() {
        assertEquals(new Value.Obj(java.util.Map.of()), Ktav.loads(new byte[0]));
        assertEquals(new Value.Obj(java.util.Map.of()), Ktav.loadsStrict(new byte[0]));
    }

    @Test
    void byteArrayNullRetainsSrcNullCheck() {
        assertEquals("src", assertThrows(NullPointerException.class,
                () -> Ktav.loads((byte[]) null)).getMessage());
        assertEquals("src", assertThrows(NullPointerException.class,
                () -> Ktav.loadsStrict((byte[]) null)).getMessage());
    }
}
