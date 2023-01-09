package net.ornithemc.mappingutils.io.tiny;

import java.io.BufferedWriter;

import net.ornithemc.mappingutils.io.Mappings;

public abstract class TinyMappingsWriter {

	protected static final String TAB = "\t";

	protected final BufferedWriter writer;
	protected final Mappings mappings;

	protected TinyMappingsWriter(BufferedWriter writer, Mappings mappings) {
		mappings.validate();

		this.writer = writer;
		this.mappings = mappings;
	}

	public void write() throws Exception {
		writeHeader();
		writeMappings();
	}

	protected abstract void writeHeader() throws Exception;

	protected abstract void writeMappings() throws Exception;

}
