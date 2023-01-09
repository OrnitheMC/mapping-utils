package net.ornithemc.mappingutils.io.tiny.v2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;
import net.ornithemc.mappingutils.io.tiny.TinyMappingsReader;

public class TinyV2Reader extends TinyMappingsReader {

	public static Mappings read(Path path) throws Exception {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		} catch (Exception e) {
			throw new IllegalStateException("error reading " + path.toString(), e);
		}
	}

	public static Mappings read(BufferedReader reader) throws Exception {
		return new TinyV2Reader(reader).read();
	}

	private int indents;

	private ClassMapping c;
	private FieldMapping f;
	private MethodMapping m;
	private ParameterMapping p;

	private TinyV2Reader(BufferedReader reader) {
		super(reader, new Mappings());
	}

	@Override
	protected Stage parseHeader(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		if (args.length != 5) {
			throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 5");
		}

		String format = args[0];
		String version = args[1];
		String minorVersion = args[2];
		String srcNamespace = args[3];
		String dstNamespace = args[4];

		if (!TinyV2Format.FORMAT.equals(format)) {
			throw new IllegalStateException("cannot read mapping format " + format + " - expected " + TinyV2Format.FORMAT);
		}
		if (!TinyV2Format.VERSION.equals(version)) {
			throw new IllegalStateException("cannot read tiny version " + version + " - expected " + TinyV2Format.VERSION);
		}
		if (!TinyV2Format.MINOR_VERSION.equals(minorVersion)) {
			throw new IllegalStateException("cannot read tiny 2 minor version " + minorVersion + " - expected " + TinyV2Format.MINOR_VERSION);
		}

		mappings.setSrcNamespace(srcNamespace);
		mappings.setDstNamespace(dstNamespace);

		return Stage.MAPPINGS;
	}

	@Override
	protected Stage parseMappings(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		for (indents = 0; indents < args.length; indents++) {
			if (!args[indents].isEmpty()) {
				break;
			}
		}

		int ac = args.length - indents;

		String src;
		String dst;
		String desc;

		switch (args[indents]) {
		case TinyV2Format.COMMENT:
//		case TinyV2Format.CLASS: // classes and comments use the same identifier
			// first check if this line is a comment
			int parentIndents = indents - 1;

			if (parentIndents == TinyV2Format.CLASS_INDENTS) {
				if (ac != 2) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for class javadocs on line " + lineNumber + " - expected 2");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read class javadocs on line " + lineNumber + " - not in a class?");
				}

				c.setJavadoc(args[1 + indents]);

				break;
			}
			if (parentIndents == TinyV2Format.FIELD_INDENTS || parentIndents == TinyV2Format.METHOD_INDENTS) {
				if (ac != 2) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for field/method javadocs one line " + lineNumber + " - expected 2");
				}
				if (f == null && m == null) {
					throw new IllegalStateException("cannot read field/method javadocs on line " + lineNumber + " - not in a field or method?");
				}

				(f == null ? m : f).setJavadoc(args[1 + indents]);

				break;
			}
			if (parentIndents == TinyV2Format.PARAMETER_INDENTS) {
				if (ac != 2) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for parameter javadocs on line " + lineNumber + " - expected 2");
				}
				if (p == null) {
					throw new IllegalStateException("cannot read parameter javadocs on line " + lineNumber + " - not in a parameter?");
				}

				p.setJavadoc(args[1 + indents]);

				break;
			}

			// it's not a comment; parse class mapping
			if (indents != TinyV2Format.CLASS_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for class mapping on line " + lineNumber + " - expected " + TinyV2Format.CLASS_INDENTS);
			}
			if (ac != 3) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for class mapping on line " + lineNumber + " - expected 3");
			}

			src = args[1 + indents];
			dst = args[2 + indents];

			c = mappings.addClass(src, ClassMapping.getSimplified(dst));
			f = null;
			m = null;
			p = null;

			break;
		case TinyV2Format.FIELD:
			if (indents != TinyV2Format.FIELD_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for field mapping on line " + lineNumber + " - expected " + TinyV2Format.FIELD_INDENTS);
			}
			if (ac != 4) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for field mapping on line " + lineNumber + " - expected 4");
			}
			if (c == null) {
				throw new IllegalStateException("cannot read field mapping on line " + lineNumber + " - not in a class?");
			}

			desc = args[1 + indents];
			src = args[2 + indents];
			dst = args[3 + indents];

			f = c.addField(src, dst, desc);
			m = null;
			p = null;

			break;
		case TinyV2Format.METHOD:
			if (indents != TinyV2Format.METHOD_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for method mapping on line " + lineNumber + " - expected " + TinyV2Format.METHOD_INDENTS);
			}
			if (ac != 4) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for method mapping on line " + lineNumber + " - expected 4");
			}
			if (c == null) {
				throw new IllegalStateException("cannot read method mapping on line " + lineNumber + " - not in a class?");
			}

			desc = args[1 + indents];
			src = args[2 + indents];
			dst = args[3 + indents];

			m = c.addMethod(src, dst, desc);
			f = null;
			p = null;

			break;
		case TinyV2Format.PARAMETER:
			if (indents != TinyV2Format.PARAMETER_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for parameter mapping on line " + lineNumber + " - expected " + TinyV2Format.PARAMETER_INDENTS);
			}
			if (ac != 4) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for parameter mapping on line " + lineNumber + " - expected 4");
			}
			if (m == null) {
				throw new IllegalStateException("cannot read paremter mapping on line " + lineNumber + " - not in a method?");
			}

			String rawIndex = args[1 + indents];
			src = args[2 + indents]; // we could ignore this argument
			dst = args[3 + indents];

			int index = Integer.parseInt(rawIndex);

			if (index < 0) {
				throw new IllegalStateException("illegal parameter index " + index + " on line " + lineNumber + " - cannot be negative!");
			}

			p = m.addParameter(src, dst, index);
			f = null;

			break;
		default:
			throw new IllegalStateException("unknown mapping target " + args[indents] + " on line " + lineNumber);
		}

		return Stage.MAPPINGS;
	}
}
