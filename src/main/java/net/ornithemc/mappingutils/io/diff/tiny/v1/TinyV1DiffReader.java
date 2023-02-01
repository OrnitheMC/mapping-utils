package net.ornithemc.mappingutils.io.diff.tiny.v1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.diff.MappingsDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffReader;

public class TinyV1DiffReader extends TinyDiffReader {

	public static MappingsDiff read(Path path) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		} catch (Exception e) {
			throw new IOException("error reading " + path.toString(), e);
		}
	}

	public static MappingsDiff read(BufferedReader reader) throws IOException {
		return new TinyV1DiffReader(reader).read();
	}

	private TinyV1DiffReader(BufferedReader reader) {
		super(reader, new MappingsDiff());
	}

	@Override
	protected Stage parseHeader(String line, int lineNumber) {
		String[] args = line.split(TAB);

		if (args.length != 1) {
			throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 1");
		}

		String version = args[0];

		if (!TinyV1Format.VERSION.equals(version)) {
			throw new IllegalStateException("cannot read tiny version " + version + " - expected " + TinyV1Format.VERSION);
		}

		return Stage.DIFFS;
	}

	@Override
	protected Stage parseDiffs(String line, int lineNumber) {
		String[] args = line.split(TAB);

		String cls;
		ClassDiff c;

		String src;
		String dstA;
		String dstB;
		String desc;

		switch (args[0]) {
		case TinyV1Format.CLASS:
			if (args.length < 2 || args.length > 4) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for class diff on line " + lineNumber + " - expected 2-4");
			}

			src = args[1];
			dstA = (args.length < 3) ? "" : ClassMapping.getSimplified(args[2]);
			dstB = (args.length < 4) ? "" : ClassMapping.getSimplified(args[3]);

			diff.addClass(src, dstA, dstB);

			break;
		case TinyV1Format.FIELD:
			if (args.length < 4 || args.length > 6) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field diff on line " + lineNumber + " - expected 4-6");
			}

			cls = args[1];
			desc = args[2];
			src = args[3];
			dstA = (args.length < 5) ? "" : args[4];
			dstB = (args.length < 6) ? "" : args[5];

			c = diff.getClass(cls);

			if (c == null) {
				throw new IllegalStateException("cannot read field diff for unknown class " + cls + " on line " + lineNumber);
			}

			c.addField(src, dstA, dstB, desc);

			break;
		case TinyV1Format.METHOD:
			if (args.length < 4 || args.length > 6) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field diff on line " + lineNumber + " - expected 4-6");
			}

			cls = args[1];
			desc = args[2];
			src = args[3];
			dstA = (args.length < 5) ? "" : args[4];
			dstB = (args.length < 6) ? "" : args[5];

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
