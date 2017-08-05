package com.hyperadvanced.webscript;

import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import org.boon.Exceptions;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO: Write Javadocs for this class.
 * Created: 01/10/2015 08:07
 *
 * @author Ewan
 */
final class TypeTest {
    public static void main(String[] args) throws NoSuchMethodException {
        System.out.println(list(Integer.class).resolveType(List.class.getTypeParameters()[0]));
        System.out.println(set(String.class).resolveType(Set.class.getTypeParameters()[0]));
        final TypeToken<Map<Long, Date>> mapTypeToken = map(Long.class, Date.class);
        System.out.println(mapTypeToken.resolveType(Map.class.getTypeParameters()[0]));
        System.out.println(mapTypeToken.resolveType(Map.class.getTypeParameters()[1]));
    }

    static <T> TypeToken<List<T>> list(Class<T> tClass) {
        return new TypeToken<List<T>>(){}
                .where(new TypeParameter<T>(){}, TypeToken.of(tClass));
    }

    static <T> TypeToken<Set<T>> set(Class<T> tClass) {
        return new TypeToken<Set<T>>(){}
                .where(new TypeParameter<T>(){}, TypeToken.of(tClass));
    }

    static <K, V> TypeToken<Map<K, V>> map(Class<K> kClass, Class<V> vClass) {
        return new TypeToken<Map<K, V>>(){}
                .where(new TypeParameter<K>() {
                }, TypeToken.of(kClass))
                .where(new TypeParameter<V>() {
                }, TypeToken.of(vClass));
    }

    static <T> T call(Class<T> tClass) {
        return Exceptions.tryIt(tClass, () -> {
            return tClass.newInstance();
        });
    }
}
