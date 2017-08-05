package com.hyperadvanced.webscript;

import net.openhft.compiler.CachedCompiler;
import net.openhft.compiler.CompilerUtils;
import org.boon.di.Inject;

import java.util.function.Function;

/**
 * TODO: Write Javadocs for this class.
 * Created: 28/09/2015 23:51
 *
 * @author Ewan
 */
public class CompilingFunctionResolver implements FunctionResolver {

    @Inject private SourcesLocator sourcesLocator;
    private final CachedCompiler compiler = CompilerUtils.CACHED_COMPILER;

    @Override
    public Function<?, ?> resolve(FunctionSignature sig) throws FunctionResolutionException {
        try {
            final Class functionClass = compiler.loadFromJava("", sourcesLocator.locate(sig.getIdentifier()).get(0));
            try {
                return (Function<?, ?>) functionClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new FunctionResolutionException(String.format("Error instantiating function %s", functionClass), sig.getIdentifier());
            }
        } catch (ClassNotFoundException e) {
            throw new FunctionResolutionException("Error compiling function from source", sig.getIdentifier());
        }
    }
}
