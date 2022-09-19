package net.ornithemc.mappingutils.io.diff.tiny.v1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffReader;

public class TinyV1DiffReader extends TinyDiffReader<TinyV1Diff> {

	public static TinyV1Diff read(Path path) throws Exception {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		}
	}

	public static TinyV1Diff read(BufferedReader reader) throws Exception {
		return new TinyV1DiffReader(reader).read();
	}

	private TinyV1DiffReader(BufferedReader reader) {
		super(reader, new TinyV1Diff());
	}

	@Override
	protected Stage parseHeader(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		if (args.length != 1) {
			throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 1");
		}

		TinyV1DiffHeader header = diff.getHeader();

		String version = args[0];

		if (!header.getTinyVersion().equals(version)) {
			throw new IllegalStateException("cannot read tiny version " + version + " - expected " + header.getTinyVersion());
		}

		return Stage.DIFFS;
	}

	@Override
	protected Stage parseDiffs(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		String cls;
		ClassDiff c;

		String src;
		String dstA;
		String dstB;
		String desc;

		switch (args[0]) {
		case TinyV1Diff.CLASS:
			if (args.length != 3 && args.length != 4) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for class diff on line " + lineNumber + " - expected 3 or 4");
			}

			src = args[1];
			dstA = args[2];
			dstB = (args.length == 3) ? "" : args[3];

			diff.addClass(src, dstA, dstB);

			break;
		case TinyV1Diff.FIELD:
			if (args.length != 5 && args.length != 6) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field diff on line " + lineNumber + " - expected 5 or 6");
			}

			cls = args[1];
			desc = args[2];
			src = args[3];
			dstA = args[4];
			dstB = (args.length == 5) ? "" : args[5];

			c = diff.getClass(cls);

			if (c == null) {
				throw new IllegalStateException("cannot read field diff for unknown class " + cls + " on line " + lineNumber);
			}

			c.addField(src, dstA, dstB, desc);

			break;
		case TinyV1Diff.METHOD:
			if (args.length != 5 && args.length != 6) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field diff on line " + lineNumber + " - expected 5 or 6");
			}

			cls = args[1];
			desc = args[2];
			src = args[3];
			dstA = args[4];
			dstB = (args.length == 5) ? "" : args[5];

			c = diff.getClass(cls);

			if (c == null) {
				throw new IllegalStateException("cannot read method diff for unknown class " + cls + " on line " + lineNumber );
			}

			c.addMethod(src, dstA, dstB, desc);

			break;
		default:
			throw new IllegalStateException("unknown diff target " + args[0] + " on line " + lineNumber);
		}

		return Stage.DIFFS;
	}
}
