package net.ornithemc.mappingutils.io.tiny;

import net.ornithemc.mappingutils.io.MappingsNamespace;

public abstract class TinyHeader<M extends TinyMappings<M>> {

	protected final M mappings;

	protected TinyHeader(M mappings) {
		this.mappings = mappings;
	}

	public String getFormat() {
		return "tiny";
	}

	public abstract String getTinyVersion();

	public String getSrcNamespace() {
		return mappings.getSrcNamespace().get();
	}

	public String getDstNamespace() {
		return mappings.getDstNamespace().get();
	}

	public void setSrcNamespace(String namespace) {
		mappings.setSrcNamespace(new MappingsNamespace(namespace));
	}

	public void setDstNamespace(String namespace) {
		mappings.setDstNamespace(new MappingsNamespace(namespace));
	}
}
