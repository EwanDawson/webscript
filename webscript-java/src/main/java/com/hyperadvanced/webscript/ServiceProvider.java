package com.hyperadvanced.webscript;

import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 01/10/2015 09:22
 *
 * @author Ewan
 */
public interface ServiceProvider<INPUT, OUTPUT> {

    Function<INPUT, OUTPUT> get();
}
