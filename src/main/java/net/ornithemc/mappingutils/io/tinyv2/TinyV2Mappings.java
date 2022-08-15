package net.ornithemc.mappingutils.io.tinyv2;

import net.ornithemc.mappingutils.io.Mappings;

public class TinyV2Mappings extends Mappings {

	static final int MINOR_VERSION = 0;

	static final String CLASS = "c";
	static final String FIELD = "f";
	static final String METHOD = "m";
	static final String PARAMETER = "p";
	static final String COMMENT = "c";

	static final int CLASS_INDENTS = 0;
	static final int FIELD_INDENTS = 1;
	static final int METHOD_INDENTS = 1;
	static final int PARAMETER_INDENTS = 2;

	String srcNamespace;
	String dstNamespace;

	public TinyV2Mappings() {
		
	}

	public TinyV2Mappings(String srcNamespace, String dstNamespace) {
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
