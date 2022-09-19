package net.ornithemc.mappingutils.io.diff.tiny;

import java.io.BufferedWriter;

import net.ornithemc.mappingutils.io.diff.MappingsDiffWriter;

public abstract class TinyDiffWriter<D extends TinyDiff<D>> extends MappingsDiffWriter<D> {

	protected static final String TAB = "\t";

	protected TinyDiffWriter(BufferedWriter writer, D diff) {
		super(writer, diff);
	}

	@Override
	public void write() throws Exception {
		writeHeader();
		writeDiffs();
	}

	protected abstract void writeHeader() throws Exception;

	protected abstract void writeDiffs() throws Exception;

}
