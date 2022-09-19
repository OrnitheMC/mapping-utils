package net.ornithemc.mappingutils.io.diff.tiny;

public abstract class TinyDiffHeader<M extends TinyDiff<M>> {

	protected final M mappings;

	protected TinyDiffHeader(M mappings) {
		this.mappings = mappings;
	}

	public String getFormat() {
		return "tiny";
	}

	public abstract String getTinyVersion();

}
