package net.ornithemc.mappingutils.io.diff.tiny;

import java.io.BufferedReader;

import net.ornithemc.mappingutils.io.diff.MappingsDiffReader;

public abstract class TinyDiffReader<D extends TinyDiff<D>> extends MappingsDiffReader<D> {

	protected static final String TAB = "\t";

	protected final D diff;

	private Stage stage;

	protected TinyDiffReader(BufferedReader reader, D diff) {
		super(reader);

		this.diff = diff;
	}

	@Override
	public D read() throws Exception {
		stage = Stage.HEADER;

		for (int lineNumber = 1; stage != null; lineNumber++) {
			stage = parseLine(reader.readLine(), lineNumber);
		}

		return diff;
	}

	private Stage parseLine(String line, int lineNumber) throws Exception {
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

	protected abstract Stage parseHeader(String line, int lineNumber) throws Exception;

	protected abstract Stage parseDiffs(String line, int lineNumber) throws Exception;

	protected enum Stage {
		HEADER, DIFFS
	}
}
