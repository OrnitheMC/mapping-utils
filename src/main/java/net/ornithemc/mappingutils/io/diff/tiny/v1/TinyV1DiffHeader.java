package net.ornithemc.mappingutils.io.diff.tiny.v1;

import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffHeader;

public class TinyV1DiffHeader extends TinyDiffHeader<TinyV1Diff> {

	protected TinyV1DiffHeader(TinyV1Diff mappings) {
		super(mappings);
	}

	@Override
	public String getTinyVersion() {
		return "v1";
	}
}
