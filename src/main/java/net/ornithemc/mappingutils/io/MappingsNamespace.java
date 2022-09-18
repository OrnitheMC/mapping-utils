package net.ornithemc.mappingutils.io;

public class MappingsNamespace {

	public static final MappingsNamespace NONE = new MappingsNamespace("");

	public static final MappingsNamespace CALAMUS = new MappingsNamespace("calamus");
	public static final MappingsNamespace FEATHER = new MappingsNamespace("feather");

	private final String namespace;

	public MappingsNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String get() {
		return namespace;
	}
}
