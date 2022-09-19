package net.ornithemc.mappingutils.io.tiny;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.MappingNamespace;

public abstract class TinyMappings<M extends TinyMappings<M>> extends Mappings {

	public TinyMappings() {
		super();
	}

	public TinyMappings(MappingNamespace srcNamespace, MappingNamespace dstNamespace) {
		super(srcNamespace, dstNamespace);
	}

	public abstract TinyHeader<M> getHeader();

}
