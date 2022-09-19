package net.ornithemc.mappingutils.io.diff.tiny;

import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public abstract class TinyDiff<M extends TinyDiff<M>> extends MappingsDiff {

	public TinyDiff() {
		super();
	}

	public abstract TinyDiffHeader<M> getHeader();

}
