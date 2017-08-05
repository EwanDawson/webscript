package com.hyperadvanced.webscript;

import java.net.URI;

/**
 * Created by Ewan on 2015-09-24.
 */
public class FunctionResolutionException extends Exception {

    private final URI uri;

    public FunctionResolutionException(URI uri) {
        this.uri = uri;
    }

    public FunctionResolutionException(String message, URI uri) {
        super(message);
        this.uri = uri;
    }

    public FunctionResolutionException(String message, Throwable cause, URI uri) {
        super(message, cause);
        this.uri = uri;
    }

    public FunctionResolutionException(Throwable cause, URI uri) {
        super(cause);
        this.uri = uri;
    }

    public FunctionResolutionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace, URI uri) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.uri = uri;
    }

    public URI getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return String.format("Could not resolve function at '%s': %s", uri, getMessage());
    }
}
