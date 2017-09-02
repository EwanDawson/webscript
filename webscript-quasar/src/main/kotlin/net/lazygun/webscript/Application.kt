package net.lazygun.webscript

import co.paralleluniverse.kotlin.fiber
import co.paralleluniverse.strands.Strand

/**
 * @author Ewan
 */

fun main(args: Array<String>) {
    (1..5).forEach { i ->
        fiber {
            (1..10).forEach {
                println(String.format("$i-%02d : Hello!", it))
                Strand.sleep(100)
            }
        }
    }
}

