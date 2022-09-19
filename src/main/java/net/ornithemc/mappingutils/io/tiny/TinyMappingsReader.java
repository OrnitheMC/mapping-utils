package net.ornithemc.mappingutils.io.tiny;

import java.io.BufferedReader;

import net.ornithemc.mappingutils.io.MappingsReader;

public abstract class TinyMappingsReader<M extends TinyMappings<M>> extends MappingsReader<M> {

	protected static final String TAB = "\t";

	protected final M mappings;

	private Stage stage;

	protected TinyMappingsReader(BufferedReader reader, M mappings) {
		super(reader);

		this.mappings = mappings;
	}

	@Override
	public M read() throws Exception {
		stage = Stage.HEADER;

		for (int lineNumber = 1; stage != null; lineNumber++) {
			stage = parseLine(reader.readLine(), lineNumber);
		}

		return mappings;
	}

	private Stage parseLine(String line, int lineNumber) throws Exception {
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

	protected abstract Stage parseHeader(String line, int lineNumber) throws Exception;

	protected abstract Stage parseMappings(String line, int lineNumber) throws Exception;

	protected enum Stage {
		HEADER, MAPPINGS
	}
}
