package net.ornithemc.mappingutils.io.tiny;

import net.ornithemc.mappingutils.io.Mappings;

public class TinyMappings extends Mappings {

	static final String CLASS = "CLASS";
	static final String FIELD = "FIELD";
	static final String METHOD = "METHOD";

	String srcNamespace;
	String dstNamespace;

	public TinyMappings() {

	}

	public TinyMappings(String srcNamespace, String dstNamespace) {
		this.srcNamespace = srcNamespace;
		this.dstNamespace = dstNamespace;
	}

	public String getSrcNamespace() {
		return srcNamespace;
	}

	public String getDstNamespace() {
		return dstNamespace;
	}
}
