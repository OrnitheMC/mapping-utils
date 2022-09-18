package net.ornithemc.mappingutils.io;

import java.io.BufferedReader;

public abstract class MappingsReader<M extends Mappings> {

	protected final BufferedReader reader;

	protected MappingsReader(BufferedReader reader) {
		this.reader = reader;
	}

	public abstract M read() throws Exception;

}
