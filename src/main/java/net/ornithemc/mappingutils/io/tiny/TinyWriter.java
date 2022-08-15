package net.ornithemc.mappingutils.io.tiny;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;

public class TinyWriter {

	private static final String TAB = "\t";

	public static void write(Path p, TinyMappings mappings) throws Exception {
		new TinyWriter(p, mappings).write();
	}

	private final Path path;
	private final TinyMappings mappings;

	private TinyWriter(Path path, TinyMappings mappings) {
		this.path = path;
		this.mappings = mappings;
	}

	public void write() throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(path.toFile()))) {
			// header
			bw.write("v1");
			bw.write(TAB);
			bw.write(mappings.srcNamespace);
			bw.write(TAB);
			bw.write(mappings.dstNamespace);
			bw.newLine();

			// mappings
			for (ClassMapping c : mappings.getTopLevelClasses()) {
				bw.write(TinyMappings.CLASS);
				bw.write(TAB);
				bw.write(c.src());
				bw.write(TAB);
				bw.write(c.get());
				bw.newLine();

				for (FieldMapping f : c.getFields()) {
					bw.write(TinyMappings.FIELD);
					bw.write(TAB);
					bw.write(c.src());
					bw.write(TAB);
					bw.write(f.getDesc());
					bw.write(TAB);
					bw.write(f.src());
					bw.write(TAB);
					bw.write(f.get());
					bw.newLine();
				}
				for (MethodMapping m : c.getMethods()) {
					bw.write(TinyMappings.METHOD);
					bw.write(TAB);
					bw.write(c.src());
					bw.write(TAB);
					bw.write(m.getDesc());
					bw.write(TAB);
					bw.write(m.src());
					bw.write(TAB);
					bw.write(m.get());
					bw.newLine();
				}
			}
		}
	}
}
