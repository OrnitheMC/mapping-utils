package net.ornithemc.mappingutils.io.diff.tiny.v2;

import net.ornithemc.mappingutils.io.diff.tiny.TinyDiff;

public class TinyV2Diff extends TinyDiff<TinyV2Diff> {

	static final String CLASS = "c";
	static final String FIELD = "f";
	static final String METHOD = "m";
	static final String PARAMETER = "p";
	static final String COMMENT = "c";

	static final int CLASS_INDENTS = 0;
	static final int FIELD_INDENTS = 1;
	static final int METHOD_INDENTS = 1;
	static final int PARAMETER_INDENTS = 2;

	private final TinyV2DiffHeader header;

	public TinyV2Diff() {
		super();

		this.header = new TinyV2DiffHeader(this);
	}

	@Override
	public TinyV2DiffHeader getHeader() {
		return header;
	}
}
