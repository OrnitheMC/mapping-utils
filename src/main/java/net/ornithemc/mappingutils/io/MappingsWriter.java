package net.ornithemc.mappingutils.io;

import java.io.BufferedWriter;

public abstract class MappingsWriter<M extends Mappings> {

	protected final BufferedWriter writer;
	protected final M mappings;

	protected int indents;

	protected MappingsWriter(BufferedWriter writer, M mappings) {
		this.writer = writer;
		this.mappings = mappings;
	}

	public abstract void write() throws Exception;

}
