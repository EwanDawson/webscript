package com.hyperadvanced.webscript;

import java.net.URI;
import java.util.List;

/**
 * TODO: Write Javadocs for this class.
 * Created: 28/09/2015 23:59
 *
 * @author Ewan
 */
public interface SourcesLocator {

    List<String> locate(URI uri);
}
