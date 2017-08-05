package com.hyperadvanced.webscript;

/**
 * TODO: Write Javadocs for this class.
 * Created: 21/09/2015 21:03
 *
 * @author Ewan
 */
public interface Function<T, R> {

    R apply(T input) throws Exception;

    Class<T> inputType();

    Class<R> returnType();
}
