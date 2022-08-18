package net.ornithemc.mappingutils.io.tinyv2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;

public class TinyV2Writer {

	private static final String TAB = "\t";

	public static void write(Path p, TinyV2Mappings mappings) throws Exception {
		new TinyV2Writer(p, mappings).write();
	}

	private final Path path;
	private final TinyV2Mappings mappings;

	private int indents;

	private TinyV2Writer(Path path, TinyV2Mappings mappings) {
		this.path = path;
		this.mappings = mappings;
	}

	public void write() throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(path.toFile()))) {
			// header
			bw.write("tiny");
			bw.write(TAB);
			bw.write("2");
			bw.write(TAB);
			bw.write(Integer.toString(TinyV2Mappings.MINOR_VERSION));
			bw.write(TAB);
			bw.write(mappings.srcNamespace);
			bw.write(TAB);
			bw.write(mappings.dstNamespace);
			bw.newLine();

			// mappings
			for (ClassMapping c : mappings.getTopLevelClasses()) {
				writeClass(bw, c);
			}
		}
	}

	private void writeClass(BufferedWriter bw, ClassMapping c) throws Exception {
		indents = TinyV2Mappings.CLASS_INDENTS;

		bw.write(indent());
		bw.write(TinyV2Mappings.CLASS);
		bw.write(TAB);
		bw.write(c.src());
		bw.write(TAB);
		bw.write(c.get());
		bw.newLine();

		for (FieldMapping f : c.getFields()) {
			writeField(bw, f);
		}
		for (MethodMapping m : c.getMethods()) {
			writeMethod(bw, m);
		}
		for (ClassMapping cc : c.getClasses()) {
			writeClass(bw, cc);
		}
	}

	private void writeField(BufferedWriter bw, FieldMapping f) throws Exception {
		indents = TinyV2Mappings.FIELD_INDENTS;

		bw.write(indent());
		bw.write(TinyV2Mappings.FIELD);
		bw.write(TAB);
		bw.write(f.getDesc());
		bw.write(TAB);
		bw.write(f.src());
		bw.write(TAB);
		bw.write(f.get());
		bw.newLine();

		writeJavadocs(bw, f);
	}

	private void writeMethod(BufferedWriter bw, MethodMapping m) throws Exception {
		indents = TinyV2Mappings.METHOD_INDENTS;

		bw.write(indent());
		bw.write(TinyV2Mappings.METHOD);
		bw.write(TAB);
		bw.write(m.getDesc());
		bw.write(TAB);
		bw.write(m.src());
		if (!m.src().equals(m.get()) && !m.src().equals("<init>") && !m.src().equals("<clinit>")) {
			bw.write(TAB);
			bw.write(m.get());
		}
		bw.newLine();

		writeJavadocs(bw, m);

		for (ParameterMapping p : m.getParameters()) {
			writeParameter(bw, p);
		}
	}

	private void writeParameter(BufferedWriter bw, ParameterMapping p) throws Exception {
		indents = TinyV2Mappings.PARAMETER_INDENTS;

		bw.write(indent());
		bw.write(TinyV2Mappings.PARAMETER);
		bw.write(TAB);
		bw.write(Integer.toString(p.getIndex()));
		bw.write(TAB);
		bw.write(p.src());
		bw.write(TAB);
		bw.write(p.get());
		bw.newLine();

		writeJavadocs(bw, p);
	}

	private void writeJavadocs(BufferedWriter bw, Mapping<?> mapping) throws Exception {
		String jav = mapping.getJavadocs();

		if (jav != null && !jav.isEmpty()) {
			indents++;

			bw.write(indent());
			bw.write(TinyV2Mappings.COMMENT);
			bw.write(TAB);
			bw.write(jav);
			bw.newLine();

			indents--;
		}
	}

	private String indent() {
		return TAB.repeat(indents);
	}
}
