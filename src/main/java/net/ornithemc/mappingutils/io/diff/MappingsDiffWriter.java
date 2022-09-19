package net.ornithemc.mappingutils.io.diff;

import java.io.BufferedWriter;

public abstract class MappingsDiffWriter<D extends MappingsDiff> {

	protected final BufferedWriter writer;
	protected final D diff;

	protected int indents;

	protected MappingsDiffWriter(BufferedWriter writer, D diff) {
		diff.validate();

		this.writer = writer;
		this.diff = diff;
	}

	public abstract void write() throws Exception;

}
