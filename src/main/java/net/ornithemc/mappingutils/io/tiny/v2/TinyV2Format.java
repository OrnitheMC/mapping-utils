package net.ornithemc.mappingutils.io.tiny.v2;

class TinyV2Format {

	static final String FORMAT = "tiny";
	static final String VERSION = "2";
	static final String MINOR_VERSION = "0";

	static final String CLASS = "c";
	static final String FIELD = "f";
	static final String METHOD = "m";
	static final String PARAMETER = "p";
	static final String COMMENT = "c";

	static final int CLASS_INDENTS = 0;
	static final int FIELD_INDENTS = CLASS_INDENTS + 1;
	static final int METHOD_INDENTS = CLASS_INDENTS + 1;
	static final int PARAMETER_INDENTS = METHOD_INDENTS + 1;

}
