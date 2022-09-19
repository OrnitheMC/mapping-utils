package net.ornithemc.mappingutils.io.diff.tiny.v2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Arrays;

import net.ornithemc.mappingutils.io.diff.DiffSide;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ClassDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.FieldDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.JavadocDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.MethodDiff;
import net.ornithemc.mappingutils.io.diff.MappingsDiff.ParameterDiff;
import net.ornithemc.mappingutils.io.diff.tiny.TinyDiffReader;

public class TinyV2DiffReader extends TinyDiffReader<TinyV2Diff> {

	public static TinyV2Diff read(Path path) throws Exception {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		}
	}

	public static TinyV2Diff read(BufferedReader reader) throws Exception {
		return new TinyV2DiffReader(reader).read();
	}

	private int indents;

	private ClassDiff c;
	private FieldDiff f;
	private MethodDiff m;
	private ParameterDiff p;
	private JavadocDiff j;

	private TinyV2DiffReader(BufferedReader reader) {
		super(reader, new TinyV2Diff());
	}

	@Override
	protected Stage parseHeader(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		if (args.length != 3) {
			throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 3");
		}

		TinyV2DiffHeader header = diff.getHeader();

		String format = args[0];
		String version = args[1];
		String minorVersion = args[2];

		if (!header.getFormat().equals(format)) {
			throw new IllegalStateException("cannot read mapping format " + format + " - expected " + header.getFormat());
		}
		if (!header.getTinyVersion().equals(version)) {
			throw new IllegalStateException("cannot read tiny version " + version + " - expected " + header.getTinyVersion());
		}
		if (!header.getMinorVersion().equals(minorVersion)) {
			throw new IllegalStateException("cannot read tiny 2 minor version " + minorVersion + " - expected " + header.getMinorVersion());
		}

		return Stage.DIFFS;
	}

	@Override
	protected Stage parseDiffs(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		for (indents = 0; indents < args.length; indents++) {
			if (!args[indents].isEmpty()) {
				break;
			}
		}

		int ac = args.length - indents;

		String src;
		String dstA;
		String dstB;
		String desc;

		switch (args[indents]) {
		case TinyV2Diff.COMMENT:
//		case TinyV2Diffs.CLASS: // classes and comments use the same identifier
			// first check if this line is a comment
			int parentIndents = indents - 1;

			if (parentIndents == TinyV2Diff.CLASS_INDENTS) {
				if (ac != 2 && ac != 3) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for class javadocs on line " + lineNumber + " - expected 2 or 3");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read class javadocs on line " + lineNumber + " - not in a class?");
				}

				dstA = args[1 + indents];
				dstB = (ac == 2) ? "" : args[2 + indents];

				j = c.getJavadoc();

				j.set(DiffSide.A, dstA);
				j.set(DiffSide.B, dstB);

				break;
			}
			if (parentIndents == TinyV2Diff.FIELD_INDENTS || parentIndents == TinyV2Diff.METHOD_INDENTS) {
				if (ac != 2 && ac != 3) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for field/method javadocs on line " + lineNumber + " - expected 2 or 3");
				}
				if (f == null && m == null) {
					throw new IllegalStateException("cannot read field/method javadocs on line " + lineNumber + " - not in a field or method?");
				}

				dstA = args[1 + indents];
				dstB = (ac == 2) ? "" : args[2 + indents];

				j = (f == null ? m : f).getJavadoc();

				j.set(DiffSide.A, dstA);
				j.set(DiffSide.B, dstB);

				break;
			}
			if (parentIndents == TinyV2Diff.PARAMETER_INDENTS) {
				if (ac != 2 && ac != 3) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for parameter javadocs on line " + lineNumber + " - expected 2 or 3");
				}
				if (p == null) {
					throw new IllegalStateException("cannot read parameter javadocs on line " + lineNumber + " - not in a parameter?");
				}

				dstA = args[1 + indents];
				dstB = (ac == 2) ? "" : args[2 + indents];

				j = p.getJavadoc();

				j.set(DiffSide.A, dstA);
				j.set(DiffSide.B, dstB);

				break;
			}

			// it's not a comment; parse class diff
			if (indents != TinyV2Diff.CLASS_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for class mapping on line " + lineNumber + " - expected " + TinyV2Diff.CLASS_INDENTS);
			}
			if (ac != 3 && ac != 4) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for class mapping on line " + lineNumber + " - expected 3 or 4");
			}

			src = args[1 + indents];
			dstA = args[2 + indents];
			dstB = (ac == 3) ? "" : args[3 + indents];

			c = diff.addClass(src, dstA, dstB);
			f = null;
			m = null;
			p = null;

			break;
		case TinyV2Diff.FIELD:
			if (indents != TinyV2Diff.FIELD_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for field mapping on line " + lineNumber + " - expected " + TinyV2Diff.FIELD_INDENTS);
			}
			if (ac != 4 && ac != 5) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for field mapping on line " + lineNumber + " - expected 4 or 5");
			}
			if (c == null) {
				throw new IllegalStateException("cannot read field mapping on line " + lineNumber + " - not in a class?");
			}

			desc = args[1 + indents];
			src = args[2 + indents];
			dstA = args[3 + indents];
			dstB = (ac == 4) ? "" : args[4 + indents];

			f = c.addField(src, dstA, dstB, desc);
			m = null;
			p = null;

			break;
		case TinyV2Diff.METHOD:
			if (indents != TinyV2Diff.METHOD_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for method mapping on line " + lineNumber + " - expected " + TinyV2Diff.METHOD_INDENTS);
			}
			if (ac != 4 && ac != 5) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for method mapping on line " + lineNumber + " - expected 4 or 5");
			}
			if (c == null) {
				throw new IllegalStateException("cannot read method mapping on line " + lineNumber + " - not in a class?");
			}

			desc = args[1 + indents];
			src = args[2 + indents];
			dstA = args[3 + indents];
			dstB = (ac == 4) ? "" : args[4 + indents];

			m = c.addMethod(src, dstA, dstB, desc);
			f = null;
			p = null;

			break;
		case TinyV2Diff.PARAMETER:
			if (indents != TinyV2Diff.PARAMETER_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for parameter mapping on line " + lineNumber + " - expected " + TinyV2Diff.PARAMETER_INDENTS);
			}
			if (ac != 4 && ac != 5) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for parameter mapping on line " + lineNumber + " - expected 4 or 5");
			}
			if (m == null) {
				throw new IllegalStateException("cannot read paremter mapping on line " + lineNumber + " - not in a method?");
			}

			String rawIndex = args[1 + indents];
			src = args[2 + indents]; // we could ignore this argument
			dstA = args[3 + indents];
			dstB = (ac == 4) ? "" : args[4 + indents];

			int index = Integer.parseInt(rawIndex);

			if (index < 0) {
				throw new IllegalStateException("illegal parameter index " + index + " on line " + lineNumber + " - cannot be negative!");
			}

			p = m.addParameter(src, dstA, dstB, index);
			f = null;

			break;
		default:
			throw new IllegalStateException("unknown mapping target " + args[indents] + " on line " + lineNumber + " - " + Arrays.toString(args));
		}

		return Stage.DIFFS;
	}
}
