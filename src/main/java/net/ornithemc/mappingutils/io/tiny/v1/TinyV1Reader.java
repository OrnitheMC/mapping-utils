package net.ornithemc.mappingutils.io.tiny.v1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.tiny.TinyMappingsReader;

public class TinyV1Reader extends TinyMappingsReader<TinyV1Mappings> {

	public static TinyV1Mappings read(Path path) throws Exception {
		try (BufferedReader reader = new BufferedReader(new FileReader(path.toFile()))) {
			return read(reader);
		}
	}

	public static TinyV1Mappings read(BufferedReader reader) throws Exception {
		return new TinyV1Reader(reader).read();
	}

	private TinyV1Reader(BufferedReader reader) {
		super(reader, new TinyV1Mappings());
	}

	@Override
	protected Stage parseHeader(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		if (args.length != 3) {
			throw new IllegalStateException("illegal number of arguments (" + args.length + ") for header - expected 3");
		}

		TinyV1Header header = mappings.getHeader();

		String version = args[0];
		String srcNamespace = args[1];
		String dstNamespace = args[2];

		if (!header.getTinyVersion().equals(version)) {
			throw new IllegalStateException("cannot read tiny version " + version + " - expected " + header.getTinyVersion());
		}

		header.setSrcNamespace(srcNamespace);
		header.setDstNamespace(dstNamespace);

		return Stage.MAPPINGS;
	}

	@Override
	protected Stage parseMappings(String line, int lineNumber) throws Exception {
		String[] args = line.split(TAB);

		String cls;
		ClassMapping c;

		String src;
		String dst;
		String desc;

		switch (args[0]) {
		case TinyV1Mappings.CLASS:
			if (args.length != 3) {
				throw new IllegalStateException("illegal number of arguments (" + args.length + ") for class mapping on line " + lineNumber + " - expected 3");
			}

			src = args[1];
			dst = args[2];

			mappings.addClass(src, ClassMapping.getSimplified(dst));

			break;
		case TinyV1Mappings.FIELD:
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
		case TinyV1Mappings.METHOD:
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
