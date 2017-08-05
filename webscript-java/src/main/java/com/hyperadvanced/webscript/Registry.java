package com.hyperadvanced.webscript;

import java.net.URI;
import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 22/09/2015 09:04
 *
 * @author Ewan
 */
public interface Registry {

    void registerFunction(URI identifier, Function function);

    Function getFunction(URI identifier);
}
