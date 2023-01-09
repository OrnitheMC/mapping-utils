package net.ornithemc.mappingutils.io.enigma.file;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.MappingTarget;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;

public class EnigmaFileWriter {

	private static final String TAB = "\t";

	public static void write(Path path, Mappings mappings) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			mappings.validate();

			write(writer, mappings);
		} catch (Exception e) {
			throw new IllegalStateException("error writing " + path.toString(), e);
		}
	}

	public static void write(BufferedWriter writer, Mappings mappings) throws Exception {
		for (ClassMapping cm : mappings.getTopLevelClasses()) {
			write(writer, cm);
		}
	}

	public static void write(Path path, ClassMapping cm) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, cm);
		} catch (Exception e) {
			throw new IllegalStateException("error writing " + path.toString(), e);
		}
	}

	public static void write(BufferedWriter writer, ClassMapping cm) throws Exception {
		new EnigmaFileWriter(writer, cm).write();
	}

	private final BufferedWriter writer;
	private final ClassMapping root;

	private int indents;

	private EnigmaFileWriter(BufferedWriter writer, ClassMapping root) {
		this.writer = writer;
		this.root = root;
	}

	public void write() throws Exception {
		writeMapping(root);
	}

	private void writeMapping(Mapping<?> m) throws Exception {
		indent();

		switch (m.target()) {
		case CLASS:
			writeClass((ClassMapping)m);
			break;
		case FIELD:
			writeField((FieldMapping)m);
			break;
		case METHOD:
			writeMethod((MethodMapping)m);
			break;
		case PARAMETER:
			writeParameter((ParameterMapping)m);
			break;
		default:
			throw new IllegalStateException("unknown mapping target " + m.target());
		}

		indents++;

		writeJavadoc(m);

		for (Mapping<?> mm : m.getChildren(MappingTarget.FIELD)) {
			writeMapping(mm);
		}
		for (Mapping<?> mm : m.getChildren(MappingTarget.METHOD)) {
			writeMapping(mm);
		}
		for (Mapping<?> mm : m.getChildren(MappingTarget.PARAMETER)) {
			writeMapping(mm);
		}
		for (Mapping<?> mm : m.getChildren(MappingTarget.CLASS)) {
			writeMapping(mm);
		}

		indents--;
	}

	private void writeClass(ClassMapping c) throws Exception {
		writer.write(EnigmaFileFormat.CLASS);
		writer.write(TAB);
		writer.write(ClassMapping.getSimplified(c.src()));
		if (!c.get().isEmpty()) {
			writer.write(TAB);
			writer.write(c.get());
		}
		writer.newLine();
	}

	private void writeField(FieldMapping f) throws Exception {
		writer.write(EnigmaFileFormat.FIELD);
		writer.write(TAB);
		writer.write(f.src());
		if (!f.get().isEmpty()) {
			writer.write(TAB);
			writer.write(f.get());
		}
		writer.write(TAB);
		writer.write(f.getDesc());
		writer.newLine();
	}

	private void writeMethod(MethodMapping m) throws Exception {
		writer.write(EnigmaFileFormat.METHOD);
		writer.write(TAB);
		writer.write(m.src());
		if (!m.get().isEmpty()) {
			writer.write(TAB);
			writer.write(m.get());
		}
		writer.write(TAB);
		writer.write(m.getDesc());
		writer.newLine();
	}

	private void writeParameter(ParameterMapping p) throws Exception {
		writer.write(EnigmaFileFormat.PARAMETER);
		writer.write(TAB);
		writer.write(Integer.toString(p.getIndex()));
		writer.write(TAB);
		writer.write(p.get());
		writer.newLine();
	}

	private void writeJavadoc(Mapping<?> m) throws Exception {
		String jav = m.getJavadoc();

		if (!jav.isEmpty()) {
			for (String s : jav.split("\\R")) {
				indent();

				writer.write(EnigmaFileFormat.COMMENT);
				writer.write(TAB);
				writer.write(s);
				writer.newLine();
			}
		}
	}

	private void indent() throws Exception {
		for (int i = 0; i < indents; i++) {
			writer.write(TAB);
		}
	}
}
