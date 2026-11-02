package ch.so.agi.jenkins.gretldatenportal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GretlDatenportalRootActionIconTest {
    @Test
    void usesJobsSymbol() {
        assertEquals("symbol-jobs", new GretlDatenportalRootAction().getIconFileName());
    }
}
