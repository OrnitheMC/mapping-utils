package net.ornithemc.mappingutils.io.diff.tiny;

import java.io.BufferedWriter;

import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public abstract class TinyDiffWriter {

	protected static final String TAB = "\t";

	protected final BufferedWriter writer;
	protected final MappingsDiff diff;

	protected TinyDiffWriter(BufferedWriter writer, MappingsDiff diff) {
		diff.validate();

		this.writer = writer;
		this.diff = diff;
	}

	public void write() throws Exception {
		writeHeader();
		writeDiffs();
	}

	protected abstract void writeHeader() throws Exception;

	protected abstract void writeDiffs() throws Exception;

}
