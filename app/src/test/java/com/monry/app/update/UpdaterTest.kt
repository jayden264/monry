package com.monry.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun comparesNumericallyNotLexically() {
        assertTrue(isNewerVersion("v1.2.10", "1.2.9"))
        assertTrue(isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(isNewerVersion("1.0.1", "1.0"))
    }

    @Test fun equalOrOlderIsNotAnUpdate() {
        assertFalse(isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(isNewerVersion("v1.0", "1.0.0"))
        assertFalse(isNewerVersion("v0.9.9", "1.0.0"))
    }
}
