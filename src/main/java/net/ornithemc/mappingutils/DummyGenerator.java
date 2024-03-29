package net.ornithemc.mappingutils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import net.ornithemc.mappingutils.io.MappingNamespace;
import net.ornithemc.mappingutils.io.Mappings;
import net.ornithemc.mappingutils.io.Mappings.ClassMapping;
import net.ornithemc.mappingutils.io.Mappings.MethodMapping;

class DummyGenerator {

	static Mappings run(MappingNamespace srcNamespace, MappingNamespace dstNamespace, String classNamePattern, Path jarPath) throws IOException {
		return new DummyGenerator(srcNamespace, dstNamespace, classNamePattern, jarPath).run();
	}

	private final MappingNamespace srcNamespace;
	private final MappingNamespace dstNamespace;
	private final Pattern classNamePattern;
	private final Path jarPath;

	private DummyGenerator(MappingNamespace srcNamespace, MappingNamespace dstNamespace, String classNamePattern, Path jarPath) throws IOException {
		this.srcNamespace = srcNamespace;
		this.dstNamespace = dstNamespace;
		this.classNamePattern = Pattern.compile(classNamePattern);
		this.jarPath = jarPath;
	}

	private Mappings run() throws IOException {
		Mappings mappings = new Mappings();

		mappings.setSrcNamespace(srcNamespace);
		mappings.setDstNamespace(dstNamespace);

		// TinyRemapper outputs classes in a practically random order,
		// so we need to sort them before generating mappings.
		Queue<ClassNode> classes = new PriorityQueue<>((c1, c2) -> {
			return c1.name.compareTo(c2.name);
		});

		try (JarInputStream jis = new JarInputStream(new FileInputStream(jarPath.toFile()))) {
			for (JarEntry entry; (entry = jis.getNextJarEntry()) != null;) {
				if (entry.getName().endsWith(".class")) {
					ClassReader reader = new ClassReader(jis);
					ClassNode visitor = new ClassNode(Opcodes.ASM9);

					reader.accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);

					classes.add(visitor);
				}
			}
		}

		while (!classes.isEmpty()) {
			ClassNode cn = classes.poll();

			if (!classNamePattern.matcher(cn.name).matches()) {
				continue;
			}

			ClassMapping c = mappings.addClass(cn.name, ClassMapping.getSimplified(cn.name));

			for (FieldNode fn : cn.fields) {
				c.addField(fn.name, fn.name, fn.desc);
			}
			for (MethodNode mn : cn.methods) {
				MethodMapping m = c.addMethod(mn.name, mn.name, mn.desc);

				if (mn.parameters == null) {
					continue; // no parameters present
				}

				Type methodType = Type.getMethodType(mn.desc);
				Type[] paramTypes = methodType.getArgumentTypes();
				int paramSizes = methodType.getArgumentsAndReturnSizes() >> 2;

				boolean isStatic = (mn.access & Opcodes.ACC_STATIC) != 0;

				int index = isStatic ? 0 : 1;
				int[] sizes = new int[paramSizes];

				for (int i = 0, j = index; i < paramTypes.length; i++) {
					Type paramType = paramTypes[i];
					int sort = paramType.getSort();

					int size = (sort == Type.LONG || sort == Type.DOUBLE) ? 2 : 1;

					sizes[j] = size;
					j += size;
				}

				for (int i = 0; i < mn.parameters.size(); i++) {
					m.addParameter("", "p_" + index, index);

					int size = sizes[index];
					index += size;
				}
			}
		}

		return mappings;
	}
}
