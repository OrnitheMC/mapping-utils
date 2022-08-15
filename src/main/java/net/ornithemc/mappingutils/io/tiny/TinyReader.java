package net.ornithemc.mappingutils.io.tiny;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;

public class TinyReader {

	public static TinyMappings read(Path p) throws Exception {
		return new TinyReader(p).read();
	}

	private final Path path;
	private final TinyMappings mappings;

	private Stage stage;

	private TinyReader(Path path) {
		this.path = path;
		this.mappings = new TinyMappings();

		this.stage = Stage.HEADER;
	}

	public TinyMappings read() throws Exception {
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
			if (args.length != 3) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 3");
			}

			String version = args[0];
			String srcNamespace = args[1];
			String dstNamespace = args[2];

			if (!version.equals("v1")) {
				throw new IllegalStateException("cannot read tiny version " + version + " - expected v1");
			}

			mappings.srcNamespace = srcNamespace;
			mappings.dstNamespace = dstNamespace;

			return Stage.MAPPINGS;
		case MAPPINGS:
			String target = args[0];

			String cls;
			ClassMapping c;

			String src;
			String dst;
			String desc;

			switch (target) {
			case TinyMappings.CLASS:
				if (args.length != 3) {
					throw new IllegalStateException("illegal number of arguments (" + args.length + ") for class mapping - expected 3");
				}

				src = args[1];
				dst = args[2];

				mappings.addClass(src, dst);

				break;
			case TinyMappings.FIELD:
				if (args.length != 5) {
					throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field mapping - expected 5");
				}

				cls = args[1];
				desc = args[2];
				src = args[3];
				dst = args[4];

				c = mappings.getClass(cls);

				if (c == null) {
					throw new IllegalStateException("cannot read field mapping for unknown class " + cls);
				}

				c.addField(src, dst, desc);

				break;
			case TinyMappings.METHOD:
				if (args.length != 5) {
					throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field mapping - expected 5");
				}

				cls = args[1];
				desc = args[2];
				src = args[3];
				dst = args[4];

				c = mappings.getClass(cls);

				if (c == null) {
					throw new IllegalStateException("cannot read method mapping for unknown class " + cls);
				}

				c.addMethod(src, dst, desc);

				break;
			default:
				throw new IllegalStateException("unknown mapping target " + target);
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
