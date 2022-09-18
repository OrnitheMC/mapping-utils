package net.ornithemc.mappingutils.io.tiny;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.MappingsNamespace;

public abstract class TinyMappings<M extends TinyMappings<M>> extends Mappings {

	public TinyMappings() {
		super();
	}

	public TinyMappings(MappingsNamespace srcNamespace, MappingsNamespace dstNamespace) {
		super(srcNamespace, dstNamespace);
	}

	public abstract TinyHeader<M> getHeader();

}
