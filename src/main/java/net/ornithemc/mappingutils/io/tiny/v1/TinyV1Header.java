package net.ornithemc.mappingutils.io.tiny.v1;

import net.ornithemc.mappingutils.io.tiny.TinyHeader;

public class TinyV1Header extends TinyHeader<TinyV1Mappings> {

	protected TinyV1Header(TinyV1Mappings mappings) {
		super(mappings);
	}

	@Override
	public String getTinyVersion() {
		return "v1";
	}
}
