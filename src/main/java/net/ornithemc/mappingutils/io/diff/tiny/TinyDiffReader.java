package net.ornithemc.mappingutils.io.diff.tiny;

import java.io.BufferedReader;
import java.io.IOException;

import net.ornithemc.mappingutils.io.diff.MappingsDiff;

public abstract class TinyDiffReader {

	protected static final String TAB = "\t";

	protected final BufferedReader reader;
	protected final MappingsDiff diff;

	private Stage stage;

	protected TinyDiffReader(BufferedReader reader, MappingsDiff diff) {
		this.reader = reader;
		this.diff = diff;
	}

	public MappingsDiff read() throws IOException {
		stage = Stage.HEADER;

		for (int lineNumber = 1; stage != null; lineNumber++) {
			stage = parseLine(reader.readLine(), lineNumber);
		}

		return diff;
	}

	private Stage parseLine(String line, int lineNumber) {
		if (line == null) {
			return null;
		}

		switch (stage) {
		case HEADER:
			return parseHeader(line, lineNumber);
		case DIFFS:
			return parseDiffs(line, lineNumber);
		default:
			throw new IllegalStateException("cannot parse line while done with reading!");
		}
	}

	protected abstract Stage parseHeader(String line, int lineNumber);

	protected abstract Stage parseDiffs(String line, int lineNumber);

	protected enum Stage {
		HEADER, DIFFS
	}
}
