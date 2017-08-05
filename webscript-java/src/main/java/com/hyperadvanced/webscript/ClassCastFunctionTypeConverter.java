package com.hyperadvanced.webscript;

import com.google.common.reflect.TypeToken;

import java.util.function.Function;

/**
 * Created by Ewan on 2015-09-24.
 */
public class ClassCastFunctionTypeConverter implements FunctionTypeConverter {
    @Override
    public <Input, Output> Function<Input, Output> convert(Function<?, ?> function, Class<Input> inputClass, Class<Output> outputClass) {
        final TypeToken<?> inputType = inputType(function);
        if (!inputType.isAssignableFrom(TypeToken.of(inputClass)))
            throw new ClassCastException(String.format("Cannot cast function input type from %s to %s", inputType.getRawType(), inputClass));
        final TypeToken<?> returnType = returnType(function);
        if (!returnType.isAssignableFrom(TypeToken.of(outputClass)))
            throw new ClassCastException(String.format("Cannot cast function return type from %s to %s", returnType.getRawType(), outputClass));
        //noinspection unchecked
        return (Function<Input, Output>) function;
    }
}
