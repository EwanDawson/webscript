package com.hyperadvanced.webscript;

import com.google.common.reflect.TypeToken;

/**
 * TODO: Write Javadocs for this class.
 * Created: 25/10/2015 17:59
 *
 * @author Ewan
 */
final class DefaultTypeConverterFactory implements TypeConverterFactory {

    @Override
    public <SOURCE, DEST> TypeConverter<SOURCE, DEST> converter(TypeToken<SOURCE> from, TypeToken<DEST> to) {
        // TODO: Implement method.
        return null;
    }
}
