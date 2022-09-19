package net.ornithemc.mappingutils.io.tiny.v2;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Path;

import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.FieldMapping;
import net.ornithemc.mappingutils.io.Mappings.Mapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;
import net.ornithemc.mappingutils.io.Mappings.ParameterMapping;
import net.ornithemc.mappingutils.io.tiny.TinyMappingsWriter;

public class TinyV2Writer extends TinyMappingsWriter<TinyV2Mappings> {

	public static void write(Path path, TinyV2Mappings mappings) throws Exception {
		try (BufferedWriter writer = new BufferedWriter(new FileWriter(path.toFile()))) {
			write(writer, mappings);
		}
	}

	public static void write(BufferedWriter writer, TinyV2Mappings mappings) throws Exception {
		new TinyV2Writer(writer, mappings).write();
	}

	private TinyV2Writer(BufferedWriter writer, TinyV2Mappings mappings) {
		super(writer, mappings);
	}

	@Override
	protected void writeHeader() throws Exception {
		TinyV2Header header = mappings.getHeader();

		writer.write(header.getFormat());
		writer.write(TAB);
		writer.write(header.getTinyVersion());
		writer.write(TAB);
		writer.write(header.getMinorVersion());
		writer.write(TAB);
		writer.write(header.getSrcNamespace());
		writer.write(TAB);
		writer.write(header.getDstNamespace());
		writer.newLine();
	}

	@Override
	protected void writeMappings() throws Exception {
		for (ClassMapping c : mappings.getTopLevelClasses()) {
			writeClass(c);
		}
	}

	private void writeClass(ClassMapping c) throws Exception {
		indent(TinyV2Mappings.CLASS_INDENTS);

		writer.write(TinyV2Mappings.CLASS);
		writer.write(TAB);
		writer.write(c.src());
		writer.write(TAB);
		writer.write(c.get());
		writer.newLine();

		writeJavadoc(c);

		for (FieldMapping f : c.getFields()) {
			writeField(f);
		}
		for (MethodMapping m : c.getMethods()) {
			writeMethod(m);
		}
		for (ClassMapping cc : c.getClasses()) {
			writeClass(cc);
		}
	}

	private void writeField(FieldMapping f) throws Exception {
		indent(TinyV2Mappings.FIELD_INDENTS);

		writer.write(TinyV2Mappings.FIELD);
		writer.write(TAB);
		writer.write(f.getDesc());
		writer.write(TAB);
		writer.write(f.src());
		writer.write(TAB);
		writer.write(f.get());
		writer.newLine();

		writeJavadoc(f);
	}

	private void writeMethod(MethodMapping m) throws Exception {
		indent(TinyV2Mappings.METHOD_INDENTS);

		writer.write(TinyV2Mappings.METHOD);
		writer.write(TAB);
		writer.write(m.getDesc());
		writer.write(TAB);
		writer.write(m.src());
		writer.write(TAB);
		writer.write(m.get());
		writer.newLine();

		writeJavadoc(m);

		for (ParameterMapping p : m.getParameters()) {
			writeParameter(p);
		}
	}

	private void writeParameter(ParameterMapping p) throws Exception {
		indent(TinyV2Mappings.PARAMETER_INDENTS);

		writer.write(TinyV2Mappings.PARAMETER);
		writer.write(TAB);
		writer.write(Integer.toString(p.getIndex()));
		writer.write(TAB);
		writer.write(p.src());
		writer.write(TAB);
		writer.write(p.get());
		writer.newLine();

		writeJavadoc(p);
	}

	private void writeJavadoc(Mapping<?> mapping) throws Exception {
		String jav = mapping.getJavadoc();

		if (jav != null && !jav.isEmpty()) {
			indents++;

			indent();
			writer.write(TinyV2Mappings.COMMENT);
			writer.write(TAB);
			writer.write(jav);
			writer.newLine();

			indents--;
		}
	}

	private void indent(int indents) throws Exception {
		this.indents = indents;
		indent();
	}

	private void indent() throws Exception {
		for (int i = 0; i < indents; i++) {
			writer.write(TAB);
		}
	}
}
