package com.hyperadvanced.webscript;

import com.google.common.reflect.TypeToken;

/**
 * TODO: Write Javadocs for this class.
 * Created: 25/10/2015 17:57
 *
 * @author Ewan
 */
public interface TypeConverterFactory {

    <SOURCE, DEST> TypeConverter<SOURCE, DEST> converter(TypeToken<SOURCE> from, TypeToken<DEST> to);
}
