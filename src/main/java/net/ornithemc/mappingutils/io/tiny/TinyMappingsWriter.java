package net.ornithemc.mappingutils.io.tiny;

import java.io.BufferedWriter;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.MappingsWriter;

public abstract class TinyMappingsWriter<M extends Mappings> extends MappingsWriter<M> {

	protected static final String TAB = "\t";

	protected TinyMappingsWriter(BufferedWriter writer, M mappings) {
		super(writer, mappings);
	}

	@Override
	public void write() throws Exception {
		writeHeader();
		writeMappings();
	}

	protected abstract void writeHeader() throws Exception;

	protected abstract void writeMappings() throws Exception;

}
