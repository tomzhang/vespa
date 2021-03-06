// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

/**
 * Tests a case reported by tularam
 *
 * @author bratseth
 */
public class NoStemmingTestCase extends RuleBaseAbstractTestCase {

    public NoStemmingTestCase(String name) {
        super(name,"nostemming.sr");
    }

    /** Should rewrite correctly */
    public void testCorrectRewriting1() {
        assertSemantics("+(AND i:arts i:sciences) -i:b","i:as -i:b");
    }

    /** Should rewrite correctly too */
    public void testCorrectRewriting2() {
        assertSemantics("+(AND i:arts i:sciences i:crafts) -i:b","i:asc -i:b");
    }

    /** Should not rewrite */
    public void testNoRewriting() {
        assertSemantics("+i:a -i:s","i:a -i:s");
    }

}
