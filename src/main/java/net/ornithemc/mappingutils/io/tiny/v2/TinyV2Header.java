package net.ornithemc.mappingutils.io.tiny.v2;

import net.ornithemc.mappingutils.io.tiny.TinyHeader;

public class TinyV2Header extends TinyHeader<TinyV2Mappings> {

	protected TinyV2Header(TinyV2Mappings mappings) {
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
