package com.nk.tsn.args.test

import com.nk.tsn.args.initFromArgs
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ArgUtilTest {
    @Test fun simpleNoArgs() {
        class SimpleNoArgs
        val args = initFromArgs(SimpleNoArgs::class, arrayOf("no matter"))
        Assertions.assertNotNull(args)
    }

    @Test fun simpleArgument() {
        data class Simple(val a: String)
        val args = initFromArgs(Simple::class, arrayOf("a=value"))
        Assertions.assertNotNull(args)
        Assertions.assertEquals("value", args.a)
    }

    @Test fun booleanArgument() {
        data class Simple(val a: Boolean, val b: Boolean)
        val args = initFromArgs(Simple::class, arrayOf("a=false", "b=true"))
        Assertions.assertNotNull(args)
        Assertions.assertEquals(false, args.a)
        Assertions.assertEquals(true, args.b)
    }

    @Test fun intArgument() {
        data class Simple(val a: Int, val b: Int? = null, val c: Int?)
        val args = initFromArgs(Simple::class, arrayOf("a=12", "c=13"))
        Assertions.assertNotNull(args)
        Assertions.assertEquals(12, args.a)
        Assertions.assertEquals(null, args.b)
        Assertions.assertEquals(13, args.c)
    }
}