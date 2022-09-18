package net.ornithemc.mappingutils.io.tiny;

import java.io.BufferedReader;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.MappingsReader;

public abstract class TinyMappingsReader<M extends Mappings> extends MappingsReader<M> {

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

		while (stage != null) {
			stage = parseLine(reader.readLine());
		}

		return mappings;
	}

	private Stage parseLine(String line) throws Exception {
		if (line == null) {
			return null;
		}

		switch (stage) {
		case HEADER:
			return parseHeader(line);
		case MAPPINGS:
			return parseMappings(line);
		default:
			throw new IllegalStateException("cannot parse line while done with reading!");
		}
	}

	protected abstract Stage parseHeader(String line) throws Exception;

	protected abstract Stage parseMappings(String line) throws Exception;

	protected enum Stage {
		HEADER, MAPPINGS
	}
}
