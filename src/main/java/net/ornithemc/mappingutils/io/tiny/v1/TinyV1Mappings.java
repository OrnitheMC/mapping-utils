package net.ornithemc.mappingutils.io.tiny.v1;

import net.ornithemc.mappingutils.io.MappingsNamespace;
import net.ornithemc.mappingutils.io.tiny.TinyMappings;

public class TinyV1Mappings extends TinyMappings<TinyV1Mappings> {

	static final String CLASS = "CLASS";
	static final String FIELD = "FIELD";
	static final String METHOD = "METHOD";

	private final TinyV1Header header;

	public TinyV1Mappings() {
		super();

		this.header = new TinyV1Header(this);
	}

	public TinyV1Mappings(MappingsNamespace srcNamespace, MappingsNamespace dstNamespace) {
		super(srcNamespace, dstNamespace);

		this.header = new TinyV1Header(this);
	}

	@Override
	public TinyV1Header getHeader() {
		return header;
	}
}
