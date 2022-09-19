package net.ornithemc.mappingutils.io.diff.tiny.v1;

import net.ornithemc.mappingutils.io.diff.tiny.TinyDiff;

public class TinyV1Diff extends TinyDiff<TinyV1Diff> {

	static final String CLASS = "CLASS";
	static final String FIELD = "FIELD";
	static final String METHOD = "METHOD";

	private final TinyV1DiffHeader header;

	public TinyV1Diff() {
		super();

		this.header = new TinyV1DiffHeader(this);
	}

	@Override
	public TinyV1DiffHeader getHeader() {
		return header;
	}
}
