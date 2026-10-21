package com.bit128.bitsports

/** Reads a saved real NHL response from src/test/resources. */
fun fixture(name: String): String =
    requireNotNull(object {}.javaClass.classLoader!!.getResourceAsStream(name)) { "missing fixture $name" }
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
