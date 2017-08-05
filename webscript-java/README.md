Could use OSGi (Apache Felix) for loading/unloading/versioning of Function implementations. OSGi allows multiple service (Function) implementations to be registered with metadata (ns:name:version uri). Could create an implementation of FunctionResolver that uses OSGi.

Would still need to create an OSGi bundle for each Function, which seems to involve building a jar file with a special manifest.

JBoss Modules may be a better option, since it has less baggage (it's not a container, just a way to split dependencies into classpath-isolated modules). We should be able to implement our own custom module loader. Only trouble is, the documentation is terrible.