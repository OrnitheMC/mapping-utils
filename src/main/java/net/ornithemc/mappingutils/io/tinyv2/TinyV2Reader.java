package net.ornithemc.mappingutils.io.tinyv2;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.Arrays;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;

public class TinyV2Reader {

	public static TinyV2Mappings read(Path p) throws Exception {
		return new TinyV2Reader(p).read();
	}

	private final Path path;
	private final TinyV2Mappings mappings;

	private Stage stage;

	private ClassMapping c;
	private FieldMapping f;
	private MethodMapping m;
	private ParameterMapping p;

	private TinyV2Reader(Path path) {
		this.path = path;
		this.mappings = new TinyV2Mappings();

		this.stage = Stage.HEADER;
	}

	public TinyV2Mappings read() throws Exception {
		try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
			while (stage != null) {
				stage = parseLine(br.readLine());
			}
		}

		return mappings;
	}

	private Stage parseLine(String line) {
		if (line == null) {
			return null;
		}

		String[] args = line.split("\t");

		switch (stage) {
		case HEADER:
			if (args.length != 5) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 5");
			}

			String format = args[0];
			String version = args[1];
			String minorVersion = args[2];
			String srcNamespace = args[3];
			String dstNamespace = args[4];

			if (!format.equals("tiny")) {
				throw new IllegalStateException("cannot read mapping format " + format + " - expected tiny");
			}
			if (!version.equals("2")) {
				throw new IllegalStateException("cannot read tiny version " + version + " - expected 2");
			}
			if (!minorVersion.equals(Integer.toString(TinyV2Mappings.MINOR_VERSION))) {
				throw new IllegalStateException("cannot read tiny version " + version + " - expected " + TinyV2Mappings.MINOR_VERSION);
			}

			mappings.srcNamespace = srcNamespace;
			mappings.dstNamespace = dstNamespace;

			return Stage.MAPPINGS;
		case MAPPINGS:
			int indents = 0;

			for (; indents < args.length; indents++) {
				if (!args[indents].isEmpty()) {
					break;
				}
			}

			String target = args[indents];

			String src;
			String dst;
			String desc;

			if (target.equals(TinyV2Mappings.COMMENT)) {
				int parentIndents = indents - 1;

				if (parentIndents == TinyV2Mappings.CLASS_INDENTS) {
					if ((args.length - indents) != 2) {
						throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for class javadocs - expected 2");
					}
					if (c == null) {
						throw new IllegalStateException("cannot read class javadocs - not in a class?");
					}

					c.setJavadocs(args[1 + indents]);

					return stage;
				}
				if (parentIndents == TinyV2Mappings.FIELD_INDENTS || parentIndents == TinyV2Mappings.METHOD_INDENTS) {
					if ((args.length - indents) != 2) {
						throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for field/method javadocs - expected 2");
					}
					if (f == null && m == null) {
						throw new IllegalStateException("cannot read field/method javadocs - not in a field or method?");
					}

					(f == null ? m : f).setJavadocs(args[1 + indents]);

					return stage;
				}
				if (parentIndents == TinyV2Mappings.PARAMETER_INDENTS) {
					if ((args.length - indents) != 2) {
						throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for parameter javadocs - expected 2");
					}
					if (p == null) {
						throw new IllegalStateException("cannot read parameter javadocs - not in a parameter?");
					}

					p.setJavadocs(args[1 + indents]);

					return stage;
				}
			}

			switch (target) {
			case TinyV2Mappings.CLASS:
				if (indents != TinyV2Mappings.CLASS_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for class mapping - expected " + TinyV2Mappings.CLASS_INDENTS);
				}
				if ((args.length - indents) != 3) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for class mapping - expected 3");
				}

				src = args[1 + indents];
				dst = args[2 + indents];

				c = mappings.addClass(src, dst);
				f = null;
				m = null;
				p = null;

				break;
			case TinyV2Mappings.FIELD:
				if (indents != TinyV2Mappings.FIELD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for field mapping - expected " + TinyV2Mappings.FIELD_INDENTS);
				}
				if ((args.length - indents) != 4) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for field mapping - expected 4");
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

				break;
			case TinyV2Mappings.METHOD:
				if (indents != TinyV2Mappings.METHOD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for method mapping - expected " + TinyV2Mappings.METHOD_INDENTS);
				}

				int a = args.length - indents;

				if (a != 3 & a != 4) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for method mapping - expected 3 or 4");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read method mapping - not in a class?");
				}

				desc = args[1 + indents];
				src = args[2 + indents];
				dst = a == 3 ? src : args[3 + indents];

				m = c.addMethod(src, dst, desc);
				f = null;
				p = null;

				break;
			case TinyV2Mappings.PARAMETER:
				if (indents != TinyV2Mappings.PARAMETER_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for parameter mapping - expected " + TinyV2Mappings.PARAMETER_INDENTS);
				}
				if ((args.length - indents) != 4) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for parameter mapping - expected 4");
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

				break;
			default:
				throw new IllegalStateException("unknown mapping target " + target + " on line " + line + " - " + Arrays.toString(args));
			}

			return stage;
		default:
			throw new IllegalStateException("cannot parse line while done with reading!");
		}
	}

	private enum Stage {
		HEADER, MAPPINGS
	}
}
