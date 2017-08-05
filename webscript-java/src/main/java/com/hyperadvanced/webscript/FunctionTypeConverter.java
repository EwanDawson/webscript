package com.hyperadvanced.webscript;

import com.google.common.reflect.TypeToken;

import java.util.function.Function;

/**
 * Created by Ewan on 2015-09-24.
 */
public interface FunctionTypeConverter {

    <Input, Output> Function<Input, Output> convert(Function<?, ?> function, Class<Input> inputClass, Class<Output> outputClass);

    default TypeToken<?> inputType(final Function<?, ?> function) {
        final TypeToken<? extends Function> typeToken = TypeToken.of(function.getClass());
        return typeToken.resolveType(Function.class.getTypeParameters()[0]);
    }

    default TypeToken<?> returnType(final Function<?, ?> function) {
        final TypeToken<? extends Function> typeToken = TypeToken.of(function.getClass());
        return typeToken.resolveType(Function.class.getTypeParameters()[1]);
    }
}
