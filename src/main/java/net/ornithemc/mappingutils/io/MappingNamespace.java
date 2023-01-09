package net.ornithemc.mappingutils.io;

public class MappingNamespace {

	public static final MappingNamespace NONE = new MappingNamespace("");

	public static final MappingNamespace OFFICIAL = new MappingNamespace("official");
	public static final MappingNamespace CALAMUS = new MappingNamespace("calamus");
	public static final MappingNamespace NAMED = new MappingNamespace("named");

	private final String namespace;

	public MappingNamespace(String namespace) {
		this.namespace = namespace;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}

		if (obj instanceof MappingNamespace) {
			MappingNamespace o = (MappingNamespace)obj;
			return namespace.equals(o.namespace);
		}

		return false;
	}

	@Override
	public String toString() {
		return namespace;
	}
}
