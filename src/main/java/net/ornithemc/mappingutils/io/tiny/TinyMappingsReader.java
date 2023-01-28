package net.ornithemc.mappingutils.io.tiny;

import java.io.BufferedReader;
import java.io.IOException;

import net.ornithemc.mappingutils.io.Mappings;

public abstract class TinyMappingsReader {

	protected static final String TAB = "\t";

	protected final BufferedReader reader;
	protected final Mappings mappings;

	private Stage stage;

	protected TinyMappingsReader(BufferedReader reader, Mappings mappings) {
		this.reader = reader;
		this.mappings = mappings;
	}

	public Mappings read() throws IOException {
		stage = Stage.HEADER;

		for (int lineNumber = 1; stage != null; lineNumber++) {
			stage = parseLine(reader.readLine(), lineNumber);
		}

		return mappings;
	}

	private Stage parseLine(String line, int lineNumber) {
		if (line == null) {
			return null;
		}

		switch (stage) {
		case HEADER:
			return parseHeader(line, lineNumber);
		case MAPPINGS:
			return parseMappings(line, lineNumber);
		default:
			throw new IllegalStateException("cannot parse line while done with reading!");
		}
	}

	protected abstract Stage parseHeader(String line, int lineNumber);

	protected abstract Stage parseMappings(String line, int lineNumber);

	protected enum Stage {
		HEADER, MAPPINGS
	}
}
