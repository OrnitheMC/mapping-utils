package net.ornithemc.mappingutils.io.tiny.v2;

import net.ornithemc.mappingutils.io.MappingsNamespace;
import net.ornithemc.mappingutils.io.tiny.TinyMappings;

public class TinyV2Mappings extends TinyMappings<TinyV2Mappings> {

	static final String CLASS = "c";
	static final String FIELD = "f";
	static final String METHOD = "m";
	static final String PARAMETER = "p";
	static final String COMMENT = "c";

	static final int CLASS_INDENTS = 0;
	static final int FIELD_INDENTS = 1;
	static final int METHOD_INDENTS = 1;
	static final int PARAMETER_INDENTS = 2;

	private final TinyV2Header header;

	public TinyV2Mappings() {
		super();

		this.header = new TinyV2Header(this);
	}

	public TinyV2Mappings(MappingsNamespace srcNamespace, MappingsNamespace dstNamespace) {
		super(srcNamespace, dstNamespace);

		this.header = new TinyV2Header(this);
	}

	@Override
	public TinyV2Header getHeader() {
		return header;
	}
}
