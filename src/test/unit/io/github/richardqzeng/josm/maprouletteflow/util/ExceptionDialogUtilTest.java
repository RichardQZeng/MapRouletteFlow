// License: GPL. For details, see LICENSE file.
package io.github.richardqzeng.josm.maprouletteflow.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import io.github.richardqzeng.josm.maprouletteflow.api.UnauthorizedException;

class ExceptionDialogUtilTest {
    @Test
    void recognizesDirectAndWrappedAuthenticationFailures() {
        assertTrue(ExceptionDialogUtil.isUnauthorized(new UnauthorizedException("direct")));
        assertTrue(ExceptionDialogUtil.isUnauthorized(
                new IOException("wrapper", new UnauthorizedException("nested"))));
        assertFalse(ExceptionDialogUtil.isUnauthorized(new IOException("network")));
    }
}
