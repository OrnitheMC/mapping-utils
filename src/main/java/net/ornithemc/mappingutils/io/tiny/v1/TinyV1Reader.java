package net.ornithemc.mappingutils.io.tiny.v1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.tiny.TinyMappingsReader;

public class TinyV1Reader extends TinyMappingsReader {

	public static Mappings read(Path path, boolean cacheById) throws IOException {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader, cacheById);
		} catch (Exception e) {
			throw new IOException("error reading " + path.toString(), e);
		}
	}

	public static Mappings read(BufferedReader reader, boolean cacheById) throws IOException {
		return new TinyV1Reader(reader, cacheById).read();
	}

	private TinyV1Reader(BufferedReader reader, boolean cacheById) {
		super(reader, new Mappings(cacheById));
	}

	@Override
	protected Stage parseHeader(String line, int lineNumber) {
		String[] args = line.split(TAB);

		if (args.length != 3) {
			throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 3");
		}

		String version = args[0];
		String srcNamespace = args[1];
		String dstNamespace = args[2];

		if (!TinyV1Format.VERSION.equals(version)) {
			throw new IllegalStateException("cannot read tiny version " + version + " - expected " + TinyV1Format.VERSION);
		}

		mappings.setSrcNamespace(srcNamespace);
		mappings.setDstNamespace(dstNamespace);

		return Stage.MAPPINGS;
	}

	@Override
	protected Stage parseMappings(String line, int lineNumber) {
		String[] args = line.split(TAB);

		String cls;
		ClassMapping c;

		String src;
		String dst;
		String desc;

		switch (args[0]) {
		case TinyV1Format.CLASS:
			if (args.length != 3) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for class mapping on line " + lineNumber + " - expected 3");
			}

			src = args[1];
			dst = args[2];

			mappings.addClass(src, ClassMapping.getSimplified(dst));

			break;
		case TinyV1Format.FIELD:
			if (args.length != 5) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field mapping on line " + lineNumber + " - expected 5");
			}

			cls = args[1];
			desc = args[2];
			src = args[3];
			dst = args[4];

			c = mappings.getClass(cls);

			if (c == null) {
				throw new IllegalStateException("cannot read field mapping for unknown class " + cls + " on line " + lineNumber);
			}

			c.addField(src, dst, desc);

			break;
		case TinyV1Format.METHOD:
			if (args.length != 5) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for field mapping on line " + lineNumber + " - expected 5");
			}

			cls = args[1];
			desc = args[2];
			src = args[3];
			dst = args[4];

			c = mappings.getClass(cls);

			if (c == null) {
				throw new IllegalStateException("cannot read method mapping for unknown class " + cls + " on line " + lineNumber);
			}

			c.addMethod(src, dst, desc);

			break;
		default:
			throw new IllegalStateException("unknown mapping target " + args[0] + " on line " + lineNumber);
		}

		return Stage.MAPPINGS;
	}
}
