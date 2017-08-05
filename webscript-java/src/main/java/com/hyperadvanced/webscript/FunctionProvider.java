package com.hyperadvanced.webscript;

import java.net.URI;
import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 22/09/2015 08:54
 *
 * @author Ewan
 */
public interface FunctionProvider {

    <T, R> Function<T, R> get(URI identifier, Class<T> inputType, Class<R> returnType) throws FunctionResolutionException;

    <T, R> Function<T, R> get(String identifier, Class<T> inputType, Class<R> returnType) throws FunctionResolutionException;
}
