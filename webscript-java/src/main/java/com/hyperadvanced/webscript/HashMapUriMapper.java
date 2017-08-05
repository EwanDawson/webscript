package com.hyperadvanced.webscript;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: Write Javadocs for this class.
 * Created: 29/09/2015 00:23
 *
 * @author Ewan
 */
public class HashMapUriMapper implements UriMapper {

    private Map<URI, URI> mapping = new ConcurrentHashMap<>();

    @Override
    public URI apply(URI uri) {
        return mapping.get(uri);
    }

    public void addMapping(URI from, URI to) {
        mapping.put(from, to);
    }
}
