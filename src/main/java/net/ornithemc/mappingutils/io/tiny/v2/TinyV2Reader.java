package net.ornithemc.mappingutils.io.tiny.v2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Arrays;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;
import net.ornithemc.mappingutils.io.tiny.TinyMappingsReader;

public class TinyV2Reader extends TinyMappingsReader<TinyV2Mappings> {

	public static TinyV2Mappings read(Path path) throws Exception {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		}
	}

	public static TinyV2Mappings read(BufferedReader reader) throws Exception {
		return new TinyV2Reader(reader).read();
	}

	private int indents;

	private ClassMapping c;
	private FieldMapping f;
	private MethodMapping m;
	private ParameterMapping p;

	private boolean ignoreParameter;

	private TinyV2Reader(BufferedReader reader) {
		super(reader, new TinyV2Mappings());
	}

	@Override
	protected Stage parseHeader(String line) throws Exception {
		String[] args = line.split(TAB);

		if (args.length != 5) {
			throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 5");
		}

		TinyV2Header header = mappings.getHeader();

		String format = args[0];
		String version = args[1];
		String minorVersion = args[2];
		String srcNamespace = args[3];
		String dstNamespace = args[4];

		if (!header.getFormat().equals(format)) {
			throw new IllegalStateException("cannot read mapping format " + format + " - expected " + header.getFormat());
		}
		if (!header.getTinyVersion().equals(version)) {
			throw new IllegalStateException("cannot read tiny version " + version + " - expected " + header.getTinyVersion());
		}
		if (!header.getMinorVersion().equals(minorVersion)) {
			throw new IllegalStateException("cannot read tiny 2 minor version " + minorVersion + " - expected " + header.getMinorVersion());
		}

		header.setSrcNamespace(srcNamespace);
		header.setDstNamespace(dstNamespace);

		return Stage.MAPPINGS;
	}

	@Override
	protected Stage parseMappings(String line) throws Exception {
		String[] args = line.split(TAB);
		int ac = args.length;

		for (indents = 0; indents < args.length; indents++) {
			if (!args[indents].isEmpty()) {
				break;
			}
		}

		String src;
		String dst;
		String desc;

		switch (args[0]) {
		case TinyV2Mappings.COMMENT:
//		case TinyV2Mappings.CLASS: // classes and comments use the same identifier
			// first check if this line is a comment
			int parentIndents = indents - 1;

			if (parentIndents == TinyV2Mappings.CLASS_INDENTS) {
				if (ac != 2) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for class javadocs - expected 2");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read class javadocs - not in a class?");
				}

				c.setJavadocs(args[1 + indents]);

				break;
			}
			if (parentIndents == TinyV2Mappings.FIELD_INDENTS || parentIndents == TinyV2Mappings.METHOD_INDENTS) {
				if (ac != 2) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for field/method javadocs - expected 2");
				}
				if (f == null && m == null) {
					throw new IllegalStateException("cannot read field/method javadocs - not in a field or method?");
				}

				(f == null ? m : f).setJavadocs(args[1 + indents]);

				break;
			}
			if (parentIndents == TinyV2Mappings.PARAMETER_INDENTS) {
				if (ac != 2) {
					throw new IllegalStateException("illegal number of arguments (" + ac + ") for parameter javadocs - expected 2");
				}

				if (!ignoreParameter) {
					if (p == null) {
						throw new IllegalStateException("cannot read parameter javadocs - not in a parameter?");
					}

					p.setJavadocs(args[1 + indents]);
				}

				break;
			}

			// it's not a comment; parse class mapping
			if (indents != TinyV2Mappings.CLASS_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for class mapping - expected " + TinyV2Mappings.CLASS_INDENTS);
			}
			if (ac != 2 && ac != 3) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for class mapping - expected 2 or 3");
			}

			src = args[1 + indents];
			dst = (ac == 2) ? src : args[2 + indents];

			c = mappings.addClass(src, dst);
			f = null;
			m = null;
			p = null;

			ignoreParameter = false;

			break;
		case TinyV2Mappings.FIELD:
			if (indents != TinyV2Mappings.FIELD_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for field mapping - expected " + TinyV2Mappings.FIELD_INDENTS);
			}
			if (ac != 4) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for field mapping - expected 4");
			}
			if (c == null) {
				throw new IllegalStateException("cannot read field mapping - not in a class?");
			}

			desc = args[1 + indents];
			src = args[2 + indents];
			dst = args[3 + indents];

			f = c.addField(src, dst, desc);
			m = null;
			p = null;

			ignoreParameter = false;

			break;
		case TinyV2Mappings.METHOD:
			if (indents != TinyV2Mappings.METHOD_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for method mapping - expected " + TinyV2Mappings.METHOD_INDENTS);
			}
			if (ac != 3 & ac != 4) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for method mapping - expected 3 or 4");
			}
			if (c == null) {
				throw new IllegalStateException("cannot read method mapping - not in a class?");
			}

			desc = args[1 + indents];
			src = args[2 + indents];
			dst = (ac == 3) ? src : args[3 + indents];

			m = c.addMethod(src, dst, desc);
			f = null;
			p = null;

			ignoreParameter = false;

			break;
		case TinyV2Mappings.PARAMETER:
			if (indents != TinyV2Mappings.PARAMETER_INDENTS) {
				throw new IllegalStateException("illegal number of indents (" + indents + ") for parameter mapping - expected " + TinyV2Mappings.PARAMETER_INDENTS);
			}
			if (ac != 4) {
				throw new IllegalStateException("illegal number of arguments (" + ac + ") for parameter mapping - expected 4");
			}
			if (m == null) {
				throw new IllegalStateException("cannot read paremter mapping - not in a method?");
			}

			String rawIndex = args[1 + indents];
			src = args[2 + indents]; // we could ignore this argument
			dst = args[3 + indents];

			int index = Integer.parseInt(rawIndex);

			if (index < 0) {
				throw new IllegalStateException("illegal parameter index " + index + " - cannot be negative!");
			}

			p = m.addParameter(src, dst, index);
			f = null;

			ignoreParameter = (p == null);

			break;
		default:
			throw new IllegalStateException("unknown mapping target " + args[0] + " on line " + line + " - " + Arrays.toString(args));
		}

		return Stage.MAPPINGS;
	}
}
