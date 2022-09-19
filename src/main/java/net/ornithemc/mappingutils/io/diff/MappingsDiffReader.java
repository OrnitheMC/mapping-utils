package net.ornithemc.mappingutils.io.diff;

import java.io.BufferedReader;

public abstract class MappingsDiffReader<D extends MappingsDiff> {

	protected final BufferedReader reader;

	protected MappingsDiffReader(BufferedReader reader) {
		this.reader = reader;
	}

	public abstract D read() throws Exception;

}
