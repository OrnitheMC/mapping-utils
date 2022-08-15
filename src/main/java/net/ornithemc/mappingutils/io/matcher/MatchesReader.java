package net.ornithemc.mappingutils.io.matcher;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.matcher.Matches.ClassMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.FieldMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.MethodMatch;
import net.ornithemc.mappingutils.io.matcher.Matches.ParameterMatch;

public class MatchesReader {

	public static Matches read(Path p) throws Exception {
		return new MatchesReader(p).read();
	}

	private final Path path;
	private final Matches matches;

	private Stage stage;

	private ClassMatch c;
	private FieldMatch f;
	private MethodMatch m;
	private ParameterMatch p;

	private MatchesReader(Path path) {
		this.path = path;
		this.matches = new Matches();

		this.stage = Stage.START;
	}

	public Matches read() throws Exception {
		try (BufferedReader br = new BufferedReader(new FileReader(path.toFile()))) {
			while (stage != null) {
				stage = parseLine(br.readLine());
			}
		}

		return matches;
	}

	private Stage parseLine(String line) {
		if (line == null) {
			return null;
		}

		String[] args = line.split("\t");

		if (stage == Stage.START) {
			return Stage.HEADER;
		} else if (stage != Stage.MATCHES && line.startsWith("\t")) {
			if (line.startsWith("\t\t")) {
				switch (stage) {
				case INPUT_SRC:
				case INPUT_DST:
				case SHARED_CLASSPATH:
				case CLASSPATH_SRC:
				case CLASSPATH_DST:
					break;
				default:
					throw new IllegalStateException("cannot parse file for stage " + stage.name());
				}
			} else {
				switch (args[1]) {
				case Matches.INPUT_SRC:
					return Stage.INPUT_SRC;
				case Matches.INPUT_DST:
					return Stage.INPUT_DST;
				case Matches.SHARED_CLASSPATH:
					return Stage.SHARED_CLASSPATH;
				case Matches.CLASSPATH_SRC:
					return Stage.CLASSPATH_SRC;
				case Matches.CLASSPATH_DST:
					return Stage.CLASSPATH_DST;
				case Matches.NON_OBF_CLASS_SRC:
				case Matches.NON_OBF_CLASS_DST:
				case Matches.NON_OBF_MEMBER_SRC:
				case Matches.NON_OBF_MEMBER_DST:
					break;
				default:
					throw new IllegalStateException("illegal content in header: " + line);
				}
			}
		} else {
			if (stage != Stage.MATCHES) {
				stage = Stage.MATCHES;
			}

			int indents = 0;

			for (; indents < args.length; indents++) {
				if (!args[indents].isEmpty()) {
					break;
				}
			}

			String target = args[indents];

			String src;
			String dst;

			switch (target) {
			case Matches.CLASS:
				if (indents != Matches.CLASS_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for class match - expected " + Matches.CLASS_INDENTS);
				}
				if ((args.length - indents) != 3) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for class match - expected 3");
				}

				src = args[1 + indents];
				dst = args[2 + indents];

				c = matches.addClass(src, dst);
				f = null;
				m = null;
				p = null;
			case Matches.CLASS_UNMATCHABLE:
				break;
			case Matches.FIELD:
				if (indents != Matches.FIELD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for field match - expected " + Matches.FIELD_INDENTS);
				}
				if ((args.length - indents) != 3) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for field match - expected 3");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read field match - not in a class?");
				}

				src = args[1 + indents];
				dst = args[2 + indents];

				f = c.addField(src, dst);
				m = null;
				p = null;
			case Matches.FIELD_UNMATCHABLE:
				break;
			case Matches.METHOD:
				if (indents != Matches.METHOD_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for method match - expected " + Matches.METHOD_INDENTS);
				}
				if ((args.length - indents) != 3) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for method match - expected 3");
				}
				if (c == null) {
					throw new IllegalStateException("cannot read method match - not in a class?");
				}

				src = args[1 + indents];
				dst = args[2 + indents];

				m = c.addMethod(src, dst);
				f = null;
				p = null;
			case Matches.METHOD_UNMATCHABLE:
				break;
			case Matches.PARAMETER:
				if (indents != Matches.PARAMETER_INDENTS) {
					throw new IllegalStateException("illegal number of indents (" + indents + ") for parameter match - expected " + Matches.PARAMETER_INDENTS);
				}
				if ((args.length - indents) != 3) {
					throw new IllegalStateException("illegal number of arguments (" + (args.length - indents) + ") for parameter match - expected 3");
				}
				if (m == null) {
					throw new IllegalStateException("cannot read paremter match - not in a method?");
				}

				String rawSrcIndex = args[1 + indents];
				String rawDstIndex = args[2 + indents];

				int srcIndex = Integer.parseInt(rawSrcIndex);
				int dstIndex = Integer.parseInt(rawDstIndex);

				if (srcIndex < 0) {
					throw new IllegalStateException("illegal parameter index " + srcIndex + " - cannot be negative!");
				}
				if (dstIndex < 0) {
					throw new IllegalStateException("illegal parameter index " + dstIndex + " - cannot be negative!");
				}

				p = m.addParameter(srcIndex, dstIndex);
				f = null;
			case Matches.PARAMETER_UNMATCHABLE:
			case Matches.VARIABLE:
			case Matches.VARIABLE_UNMATCHABLE:
				break;
			default:
				throw new IllegalStateException("unknown match target " + target + " in line " + line);
			}
		}

		return stage;
	}

	private enum Stage {
		START, HEADER, INPUT_SRC, INPUT_DST, SHARED_CLASSPATH, CLASSPATH_SRC, CLASSPATH_DST, MATCHES
	}
}
