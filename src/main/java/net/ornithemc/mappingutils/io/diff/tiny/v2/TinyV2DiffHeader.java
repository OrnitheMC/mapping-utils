package net.ornithemc.mappingutils.io.diff.tiny.v2;

import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffHeader;

public class TinyV2DiffHeader extends TinyDiffHeader<TinyV2Diff> {

	protected TinyV2DiffHeader(TinyV2Diff mappings) {
		super(mappings);
	}

	@Override
	public String getTinyVersion() {
		return "2";
	}

	public String getMinorVersion() {
		return "0";
	}
}
